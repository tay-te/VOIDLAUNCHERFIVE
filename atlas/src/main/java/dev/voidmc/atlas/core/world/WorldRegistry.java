package dev.voidmc.atlas.core.world;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.core.store.SafeFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The distinct worlds Atlas has seen behind one server address. One address can lead to several worlds (a network
 * behind a proxy, a server that was reset, minigame lobbies), and Atlas keeps a separate map for each, in a folder
 * named by the world's id under the server's folder.
 *
 * <p>Kept in {@code <server dir>/worlds.json} through {@link SafeFile} (kind {@value #KIND}). Each world has an id
 * (also its folder name), a name the player can change, the seed hashes the server sent for it, the dimensions seen
 * (with their height range and whether they have a ceiling, when known), the spawn point per dimension when known,
 * and when it was first and last used.
 *
 * <p>Older Atlas builds put each world straight in {@code mp/<address>/<hex seed hash>/}. A server folder with such
 * subfolders and no {@code worlds.json} is adopted on load: each folder becomes a world with that id, named
 * "World 1", "World 2"... from the oldest folder (by last-modified time) to the newest.
 *
 * <p>Thread-safe. Changes are in memory until {@link #save}.
 */
public final class WorldRegistry {
	public static final String FILE = "worlds.json";
	public static final String KIND = "worlds";
	public static final int VERSION = 1;
	/** Folders older builds named by the world's seed hash in hex. */
	private static final Pattern LEGACY_FOLDER = Pattern.compile("[0-9a-fA-F]{1,16}");
	/** Folders this registry names new worlds by. */
	private static final Pattern NEW_FOLDER = Pattern.compile("world-(\\d{1,9})");
	private static final Pattern NAMED = Pattern.compile("World (\\d{1,9})");

	/** A block position: where a dimension's spawn point is. */
	public record Spawn(int x, int y, int z) {
	}

	/** What the client knows of a dimension's shape without its name: ceiling (like the Nether), bottom, height. */
	public record DimensionFacts(boolean hasCeiling, int minY, int height) {
	}

	/**
	 * One world. Collections are unmodifiable; change a world through the registry.
	 *
	 * @param dimensions every dimension the player has been in, in this world
	 * @param facts      the shape of those dimensions, for the ones it is known of
	 */
	public record World(String id, String name, Set<Long> seedHashes, Set<String> dimensions, Map<String, DimensionFacts> facts,
		Map<String, Spawn> spawns, long created, long lastUsed) {
		public World {
			seedHashes = Set.copyOf(seedHashes);
			dimensions = Set.copyOf(dimensions);
			facts = Map.copyOf(facts);
			spawns = Map.copyOf(spawns);
		}

		World with(String name, Set<Long> seeds, Set<String> dims, Map<String, DimensionFacts> facts, Map<String, Spawn> spawns, long lastUsed) {
			return new World(this.id, name, seeds, dims, facts, spawns, this.created, lastUsed);
		}
	}

	private final Path dir;
	private final SafeFile files;
	private final Map<String, World> worlds = new LinkedHashMap<>();
	private boolean migrated;
	private boolean lost;

	private WorldRegistry(Path dir, SafeFile files) {
		this.dir = dir;
		this.files = files;
	}

	/** An empty registry for a server folder, not yet saved. */
	public static WorldRegistry empty(SafeFile files, Path serverDir) {
		return new WorldRegistry(serverDir, files);
	}

	/**
	 * Loads a server folder's registry. With no {@code worlds.json} (an older Atlas, or a file lost beyond its
	 * backups), the world folders already there are adopted and the result saved straight away.
	 */
	public static WorldRegistry load(SafeFile files, Path serverDir) {
		WorldRegistry registry = new WorldRegistry(serverDir, files);
		SafeFile.Result result = files.read(serverDir.resolve(FILE), KIND, VERSION, text -> {
			try {
				decode(text);
				return true;
			} catch (RuntimeException e) {
				return false;
			}
		});
		if (result.present()) {
			for (World w : decode(result.payload())) {
				registry.worlds.put(w.id(), w);
			}
			return registry;
		}
		registry.lost = result.lost();
		if (registry.adoptFolders() > 0) {
			registry.migrated = true;
			try {
				registry.save();
			} catch (IOException e) {
				// kept in memory; the next save tries again
			}
		}
		return registry;
	}

	/**
	 * Registers every world folder in the server folder that is not registered yet (old seed-hash folders, and this
	 * registry's own {@code world-N} folders if {@code worlds.json} was lost), oldest first. Returns how many.
	 */
	private int adoptFolders() {
		if (!Files.isDirectory(this.dir)) {
			return 0;
		}
		record Found(Path path, String name, long modified) {
		}
		List<Found> found = new ArrayList<>();
		try (Stream<Path> list = Files.list(this.dir)) {
			for (Path p : (Iterable<Path>) list::iterator) {
				String name = p.getFileName().toString();
				if (!Files.isDirectory(p) || this.worlds.containsKey(name)) {
					continue;
				}
				if (LEGACY_FOLDER.matcher(name).matches() || NEW_FOLDER.matcher(name).matches()) {
					found.add(new Found(p, name, Files.getLastModifiedTime(p).toMillis()));
				}
			}
		} catch (IOException e) {
			return 0;
		}
		found.sort(Comparator.comparingLong(Found::modified).thenComparing(Found::name));
		for (Found f : found) {
			Set<Long> seeds = new TreeSet<>();
			if (LEGACY_FOLDER.matcher(f.name()).matches()) {
				long seed = Long.parseUnsignedLong(f.name(), 16);
				if (seed != 0) {
					seeds.add(seed);
				}
			}
			this.worlds.put(f.name(), new World(f.name(), this.nextName(), seeds, Set.of(), Map.of(), Map.of(), f.modified(), f.modified()));
		}
		return found.size();
	}

	public Path dir() {
		return this.dir;
	}

	public Path file() {
		return this.dir.resolve(FILE);
	}

	/** The folder a world's map is kept in. */
	public Path folder(String id) {
		return this.dir.resolve(id);
	}

	/** Whether {@link #load} adopted old world folders (and saved the result). */
	public synchronized boolean migrated() {
		return this.migrated;
	}

	/** Whether {@code worlds.json} was damaged with no readable backup (worlds were re-adopted from their folders). */
	public synchronized boolean lost() {
		return this.lost;
	}

	/** Every world, oldest first. */
	public synchronized List<World> worlds() {
		List<World> out = new ArrayList<>(this.worlds.values());
		out.sort(Comparator.comparingLong(World::created).thenComparing(World::id));
		return out;
	}

	public synchronized World get(String id) {
		return this.worlds.get(id);
	}

	public synchronized int size() {
		return this.worlds.size();
	}

	/** A new world, named "World N", with a {@code world-N} id no existing world or folder uses. */
	public synchronized World create(long now) {
		int n = 1;
		while (this.worlds.containsKey("world-" + n) || Files.exists(this.folder("world-" + n))) {
			n++;
		}
		World w = new World("world-" + n, this.nextName(), Set.of(), Set.of(), Map.of(), Map.of(), now, now);
		this.worlds.put(w.id(), w);
		return w;
	}

	/** Renames a world (blank names are refused: the list needs something to show). */
	public synchronized World rename(String id, String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("a world needs a name");
		}
		World w = this.require(id);
		return this.replace(w.with(name.strip(), w.seedHashes(), w.dimensions(), w.facts(), w.spawns(), w.lastUsed()));
	}

	/** Records a seed hash the server sent for this world; 0 (hidden) is ignored. */
	public synchronized World noteSeed(String id, long seedHash) {
		World w = this.require(id);
		if (seedHash == 0 || w.seedHashes().contains(seedHash)) {
			return w;
		}
		Set<Long> seeds = new TreeSet<>(w.seedHashes());
		seeds.add(seedHash);
		return this.replace(w.with(w.name(), seeds, w.dimensions(), w.facts(), w.spawns(), w.lastUsed()));
	}

	/** Records that the player was in {@code dimension}, and its shape if known ({@code facts} may be null). */
	public synchronized World noteDimension(String id, String dimension, DimensionFacts facts) {
		World w = this.require(id);
		Set<String> dims = new TreeSet<>(w.dimensions());
		dims.add(dimension);
		Map<String, DimensionFacts> f = new TreeMap<>(w.facts());
		if (facts != null) {
			f.put(dimension, facts);
		}
		return this.replace(w.with(w.name(), w.seedHashes(), dims, f, w.spawns(), w.lastUsed()));
	}

	/** Records a dimension's spawn point. */
	public synchronized World noteSpawn(String id, String dimension, Spawn spawn) {
		World w = this.require(id);
		Map<String, Spawn> s = new TreeMap<>(w.spawns());
		s.put(dimension, spawn);
		return this.replace(w.with(w.name(), w.seedHashes(), w.dimensions(), w.facts(), s, w.lastUsed()));
	}

	/** Marks a world as used at {@code now}. */
	public synchronized World touch(String id, long now) {
		World w = this.require(id);
		return this.replace(w.with(w.name(), w.seedHashes(), w.dimensions(), w.facts(), w.spawns(), Math.max(w.lastUsed(), now)));
	}

	/** Forgets a world (its folder is the caller's business). */
	public synchronized boolean remove(String id) {
		return this.worlds.remove(id) != null;
	}

	public synchronized void save() throws IOException {
		this.files.write(this.file(), KIND, VERSION, encode(this.worlds()));
	}

	private World require(String id) {
		World w = this.worlds.get(id);
		if (w == null) {
			throw new IllegalArgumentException("no world '" + id + "'");
		}
		return w;
	}

	private World replace(World w) {
		this.worlds.put(w.id(), w);
		return w;
	}

	/** "World N" for the lowest N above the number of worlds that no world is called yet. */
	private String nextName() {
		Set<String> taken = new TreeSet<>();
		for (World w : this.worlds.values()) {
			Matcher m = NAMED.matcher(w.name());
			if (m.matches()) {
				taken.add(w.name());
			}
		}
		int n = this.worlds.size() + 1;
		while (taken.contains("World " + n)) {
			n++;
		}
		return "World " + n;
	}

	// ---------------------------------------------------------------- JSON

	static String encode(List<World> worlds) {
		JsonArray list = new JsonArray();
		for (World w : worlds) {
			JsonObject o = new JsonObject();
			o.addProperty("id", w.id());
			o.addProperty("name", w.name());
			JsonArray seeds = new JsonArray();
			new TreeSet<>(w.seedHashes()).forEach(seeds::add);
			o.add("seedHashes", seeds);
			JsonArray dims = new JsonArray();
			for (String d : new TreeSet<>(w.dimensions())) {
				JsonObject dim = new JsonObject();
				dim.addProperty("id", d);
				DimensionFacts f = w.facts().get(d);
				if (f != null) {
					dim.addProperty("ceiling", f.hasCeiling());
					dim.addProperty("minY", f.minY());
					dim.addProperty("height", f.height());
				}
				dims.add(dim);
			}
			o.add("dimensions", dims);
			JsonObject spawns = new JsonObject();
			new TreeMap<>(w.spawns()).forEach((d, s) -> {
				JsonArray xyz = new JsonArray();
				xyz.add(s.x());
				xyz.add(s.y());
				xyz.add(s.z());
				spawns.add(d, xyz);
			});
			o.add("spawns", spawns);
			o.addProperty("created", w.created());
			o.addProperty("lastUsed", w.lastUsed());
			list.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("worlds", list);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	static List<World> decode(String text) {
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		List<World> out = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("worlds")) {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			if (id.isBlank() || id.contains("/") || id.contains("\\") || id.startsWith(".")) {
				throw new IllegalArgumentException("bad world id '" + id + "'");
			}
			Set<Long> seeds = new TreeSet<>();
			if (o.has("seedHashes")) {
				for (JsonElement s : o.getAsJsonArray("seedHashes")) {
					seeds.add(s.getAsLong());
				}
			}
			Set<String> dims = new TreeSet<>();
			Map<String, DimensionFacts> facts = new TreeMap<>();
			if (o.has("dimensions")) {
				for (JsonElement d : o.getAsJsonArray("dimensions")) {
					JsonObject dim = d.getAsJsonObject();
					String name = dim.get("id").getAsString();
					dims.add(name);
					if (dim.has("ceiling") && dim.has("minY") && dim.has("height")) {
						facts.put(name, new DimensionFacts(dim.get("ceiling").getAsBoolean(), dim.get("minY").getAsInt(), dim.get("height").getAsInt()));
					}
				}
			}
			Map<String, Spawn> spawns = new TreeMap<>();
			if (o.has("spawns")) {
				for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("spawns").entrySet()) {
					JsonArray xyz = s.getValue().getAsJsonArray();
					spawns.put(s.getKey(), new Spawn(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
				}
			}
			String name = o.has("name") ? o.get("name").getAsString() : id;
			long created = o.has("created") ? o.get("created").getAsLong() : 0;
			long lastUsed = o.has("lastUsed") ? o.get("lastUsed").getAsLong() : created;
			out.add(new World(id, name, seeds, dims, facts, spawns, created, lastUsed));
		}
		return out;
	}
}
