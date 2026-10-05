package dev.voidmc.atlas.client;

import dev.voidmc.atlas.api.AtlasWorlds;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.RegionStore;
import dev.voidmc.atlas.core.WaypointStore;
import dev.voidmc.atlas.core.store.SafeFile;
import dev.voidmc.atlas.core.store.StorageEvent;
import dev.voidmc.atlas.core.world.WorldMatcher;
import dev.voidmc.atlas.core.world.WorldRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Which stored map a level on a server belongs to (see {@link AtlasWorlds}). On every new level (joining, a
 * dimension change, a proxy moving the player to another server) Atlas holds back from mapping and compares the first
 * chunks' surfaces with the maps it keeps for that address, on a thread of its own; then it picks the map, starts a new
 * one, or starts a new one and asks. Nothing is written to a map before that, so two worlds' terrain never mix.
 * Singleplayer worlds are their own save folder and need none of this.
 */
public final class Worlds implements AtlasWorlds {
	/** How long to wait for terrain before deciding on the other signals. */
	public static final long TIMEOUT_MILLIS = 5_000;
	/** How long a question waits before the default ("keep separate"). */
	public static final long QUESTION_MILLIS = 30_000;

	private final AtlasClient atlas;
	private final List<Consumer<Notice>> listeners = new CopyOnWriteArrayList<>();
	private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Atlas world finder");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private final SafeFile files = new SafeFile(this::storage);

	private @Nullable WorldRegistry registry;
	private @Nullable Path serverDir;
	private String server = "";
	private String address = "";
	private volatile @Nullable Finding finding;
	private volatile @Nullable Question question;
	private volatile @Nullable String currentId;

	/** A level being identified. */
	private final class Finding {
		final WorldMatcher matcher;
		final String dimension;
		final long seedHash;
		final WorldRegistry.DimensionFacts facts;
		final WorldRegistry.@Nullable Spawn spawn;
		final @Nullable String previous;
		final boolean dimensionChanged;
		volatile WorldMatcher.@Nullable Decision decision;
		final Map<Long, RegionRaw> regions = new HashMap<>();

		Finding(WorldMatcher.Signals signals, @Nullable String previous, boolean dimensionChanged) {
			this.dimension = signals.dimension();
			this.seedHash = signals.seedHash();
			this.facts = signals.facts();
			this.spawn = signals.spawn();
			this.previous = previous;
			this.dimensionChanged = dimensionChanged;
			this.matcher = new WorldMatcher(signals, Worlds.this.registry.worlds(), this::stored, System.currentTimeMillis(), TIMEOUT_MILLIS);
		}

		/** A stored world's surface heights for a chunk (finder thread): the open map's own, or the files'. */
		int[] stored(String worldId, String dim, int cx, int cz) {
			int rx = Coords.region(cx << 4);
			int rz = Coords.region(cz << 4);
			RegionRaw raw = null;
			MapSession session = Worlds.this.atlas.session();
			if (worldId.equals(this.previous) && session != null) {
				DimensionData d = session.dimensionIfOpen(dim);
				LayerData layer = d == null ? null : d.layer(DimensionData.SURFACE);
				raw = layer == null ? null : layer.raw(rx, rz, false);
			}
			if (raw == null) {
				long key = (long) worldId.hashCode() << 32 ^ Coords.pack(rx, rz) ^ (long) dim.hashCode() * 31;
				raw = this.regions.computeIfAbsent(key, k -> {
					Path root = Worlds.this.registry.folder(worldId).resolve(dim.replace(':', '/'));
					RegionRaw r = new RegionStore(root, s -> {
					}).loadRaw(DimensionData.SURFACE, rx, rz);
					return r == null ? new RegionRaw(Integer.MIN_VALUE, Integer.MIN_VALUE) : r;
				});
				if (raw.x == Integer.MIN_VALUE) {
					return null;
				}
			}
			int[] out = new int[256];
			int x0 = Coords.inRegion(cx << 4);
			int z0 = Coords.inRegion(cz << 4);
			boolean any = false;
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					int i = RegionRaw.index(x0 + x, z0 + z);
					short h = raw.height[i];
					out[z * 16 + x] = raw.known(i) ? h : WorldMatcher.UNKNOWN;
					any |= raw.known(i);
				}
			}
			return any ? out : null;
		}
	}

	public Worlds(AtlasClient atlas) {
		this.atlas = atlas;
	}

	private void storage(StorageEvent e) {
		if (e.kind() == StorageEvent.Kind.RESTORED) {
			this.notice(new Notice(Notice.Kind.RESTORED, "Map restored from backup", e.file().getFileName().toString()));
		}
	}

	void notice(Notice n) {
		for (Consumer<Notice> l : this.listeners) {
			try {
				l.accept(n);
			} catch (RuntimeException e) {
				AtlasClient.LOGGER.warn("Atlas: a world listener failed: {}", e.toString());
			}
		}
	}

	/**
	 * A new level on a server (client thread). Returns null: the level is being identified (call {@link #tick} until
	 * it gives a key, and {@link #chunk} with the chunks that arrive meanwhile).
	 */
	public void levelChanged(Minecraft mc, ClientLevel level, String address, long seedHash, @Nullable String previousKey, @Nullable String previousDim) {
		Path dir = mc.gameDirectory.toPath().resolve("atlas").resolve("mp").resolve(AtlasClient.sanitise(address));
		if (this.serverDir == null || !this.serverDir.equals(dir)) {
			this.serverDir = dir;
			this.registry = WorldRegistry.load(this.files, dir);
			this.question = null;
			this.currentId = null;
		}
		this.address = address;
		this.server = AtlasClient.sanitise(address);
		String dim = level.dimension().identifier().toString();
		var facts = new WorldRegistry.DimensionFacts(level.dimensionType().hasCeiling(), level.getMinY(), level.getHeight());
		WorldRegistry.Spawn spawn = null;
		try {
			var pos = level.getRespawnData().pos();
			spawn = new WorldRegistry.Spawn(pos.getX(), pos.getY(), pos.getZ());
		} catch (RuntimeException ignored) {
			// not known yet
		}
		String previous = previousKey != null && previousKey.startsWith("mp/" + this.server + "/") ? previousKey.substring(previousKey.lastIndexOf('/') + 1) : null;
		this.finding = new Finding(new WorldMatcher.Signals(seedHash, dim, facts, spawn), previous, previousDim != null && !previousDim.equals(dim));
	}

	/** Whether a level is being identified (mapping waits). */
	public boolean finding() {
		return this.finding != null;
	}

	/** A chunk arrived while identifying (client thread): its surface goes to the finder. */
	public void chunk(LevelChunk chunk) {
		Finding f = this.finding;
		if (f == null) {
			return;
		}
		int[] heights = new int[256];
		int minY = chunk.getMinY();
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				// the highest block that stops movement or holds water, as the map keeps it (plants not counted)
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				heights[z * 16 + x] = top < minY ? WorldMatcher.UNKNOWN : top;
			}
		}
		var fp = new WorldMatcher.Fingerprint(chunk.getPos().x(), chunk.getPos().z(), heights);
		this.thread.execute(() -> {
			if (this.finding == f) {
				f.matcher.offer(fp);
				f.decision = f.matcher.decision(System.currentTimeMillis());
			}
		});
	}

	/**
	 * Once a tick while identifying (client thread): the session key to open ({@code mp/<address>/<world>}) once
	 * decided, else null.
	 */
	public @Nullable String tick() {
		Finding f = this.finding;
		Question q = this.question;
		if (q != null && System.currentTimeMillis() - q.askedAt() > QUESTION_MILLIS) {
			// no answer: the default, keep it separate
			this.question = null;
		}
		if (f == null) {
			return null;
		}
		this.thread.execute(() -> {
			if (this.finding == f) {
				f.decision = f.matcher.decision(System.currentTimeMillis());
			}
		});
		WorldMatcher.Decision d = f.decision;
		if (d == null || d.verdict() == WorldMatcher.Verdict.WAIT) {
			return null;
		}
		this.finding = null;
		String id;
		boolean continuing = d.verdict() == WorldMatcher.Verdict.MATCH && d.worldId().equals(f.previous);
		if (d.verdict() == WorldMatcher.Verdict.MATCH && (d.confidence() == WorldMatcher.Confidence.SURE || continuing)) {
			// the same world (a portal, a rejoin): on we go
			id = d.worldId();
		} else {
			boolean hadWorlds = this.registry.size() > 0;
			id = this.registry.create(System.currentTimeMillis()).id();
			String candidate = d.verdict() == WorldMatcher.Verdict.MATCH ? d.worldId() : d.bestCandidate();
			if (d.confidence() == WorldMatcher.Confidence.UNSURE && candidate != null) {
				WorldRegistry.World c = this.registry.get(candidate);
				this.question = new Question(id, candidate, c == null ? candidate : c.name(), this.address, System.currentTimeMillis());
				this.notice(new Notice(Notice.Kind.UNSURE_WORLD, "New world detected on " + this.address, c == null ? "" : c.name()));
			} else if (hadWorlds) {
				this.notice(new Notice(Notice.Kind.NEW_WORLD, "New world detected on " + this.address, ""));
			}
		}
		String world = id;
		long now = System.currentTimeMillis();
		this.thread.execute(() -> {
			this.registry.noteSeed(world, f.seedHash);
			this.registry.noteDimension(world, f.dimension, f.facts);
			if (f.spawn != null) {
				this.registry.noteSpawn(world, f.dimension, f.spawn);
			}
			this.registry.touch(world, now);
			this.save();
		});
		this.currentId = id;
		AtlasClient.LOGGER.info("VOID Atlas: {} on {} is map {} ({})", f.dimension, this.address, id, d.reason());
		return "mp/" + this.server + "/" + id;
	}

	private void save() {
		try {
			this.registry.save();
		} catch (IOException e) {
			AtlasClient.LOGGER.warn("Atlas: could not save the worlds of {}: {}", this.address, e.toString());
		}
	}

	/** Left the server. */
	public void disconnected() {
		this.finding = null;
		this.question = null;
		this.currentId = null;
	}

	// --- the API ---------------------------------------------------------------------------------------------------

	@Override
	public Optional<WorldInfo> current() {
		return this.list().stream().filter(WorldInfo::current).findFirst();
	}

	@Override
	public List<WorldInfo> list() {
		List<WorldInfo> out = new ArrayList<>();
		MapSession session = this.atlas.session();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() != null && session != null) {
			String name = session.worldKey.substring(session.worldKey.indexOf('/') + 1);
			out.add(new WorldInfo(session.worldKey, "Singleplayer · " + name, "singleplayer", true, System.currentTimeMillis(), session.places.size()));
			return out;
		}
		WorldRegistry r = this.registry;
		if (r == null) {
			return out;
		}
		String cur = this.currentId;
		for (WorldRegistry.World w : r.worlds()) {
			boolean here = w.id().equals(cur);
			int places = here && session != null ? session.places.size() : -1;
			out.add(new WorldInfo(w.id(), w.name(), this.address, here, w.lastUsed(), places));
		}
		out.sort((a, b) -> a.current() != b.current() ? (a.current() ? -1 : 1) : Long.compare(b.lastUsed(), a.lastUsed()));
		return out;
	}

	@Override
	public void rename(String id, String name) {
		WorldRegistry r = this.registry;
		if (r != null && name != null && !name.isBlank()) {
			r.rename(id, name.strip());
			this.thread.execute(this::save);
		}
	}

	@Override
	public Optional<Question> question() {
		return Optional.ofNullable(this.question);
	}

	@Override
	public void answer(boolean sameAsBefore) {
		Question q = this.question;
		this.question = null;
		if (q == null || !sameAsBefore || this.registry == null) {
			return;
		}
		// the same world after all: the new map's waypoints go into the earlier one, and the new one goes
		Path from = this.registry.folder(q.newWorld());
		Path to = this.registry.folder(q.candidate());
		this.atlas.closeSessionForMerge();
		this.thread.execute(() -> {
			try {
				merge(from, to);
				this.registry.remove(q.newWorld());
				this.save();
			} catch (IOException e) {
				AtlasClient.LOGGER.warn("Atlas: could not merge {} into {}: {}", from, to, e.toString());
			}
			this.currentId = q.candidate();
			Minecraft.getInstance().execute(() -> this.atlas.reopen("mp/" + this.server + "/" + q.candidate()));
		});
	}

	/**
	 * Folds a map into another: its waypoints are added to the other's; its terrain and places are let go (each map's
	 * region files speak its own block palette, so they cannot be moved across; the land is mapped again as the player
	 * goes, from the earlier map onwards).
	 */
	static void merge(Path from, Path to) throws IOException {
		if (!Files.isDirectory(from)) {
			return;
		}
		Path fromWaypoints = from.resolve("waypoints.json");
		if (Files.isRegularFile(fromWaypoints)) {
			WaypointStore a = new WaypointStore(fromWaypoints, s -> {
			});
			WaypointStore b = new WaypointStore(to.resolve("waypoints.json"), s -> {
			});
			a.load();
			b.load();
			a.all().forEach(b::put);
			b.save();
		}
		try (var walk = Files.walk(from)) {
			for (Path p : walk.sorted((x, y) -> y.getNameCount() - x.getNameCount()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}

	@Override
	public void addListener(Consumer<Notice> listener) {
		this.listeners.add(listener);
	}
}
