package dev.voidmc.atlas.core;

import com.google.gson.JsonArray;
import dev.voidmc.atlas.api.Waypoint;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * One world's waypoints, kept in {@code waypoints.json} next to its map. Thread-safe: the map screen, the minimap and
 * the API read snapshots while the player edits. Saved atomically (temporary file, then moved into place); a file
 * that will not parse is kept as {@code waypoints.json.corrupt} and the list starts empty rather than losing it silently.
 */
public final class WaypointStore {
	private static final int VERSION = 1;
	/** Death points kept per dimension; older ones are dropped. */
	public static final int DEATHS_KEPT = 3;

	private final Path file;
	private final Consumer<String> warn;
	private final List<Waypoint> waypoints = new ArrayList<>();
	private long version;
	private dev.voidmc.atlas.core.store.SafeFile files;

	public WaypointStore(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	/**
	 * Keeps the file through {@link dev.voidmc.atlas.core.store.SafeFile}: a checked header, rolling backups, and the
	 * newest good backup restored if the file is damaged. An older plain file is read once and written the new way.
	 */
	public WaypointStore withSafeFile(dev.voidmc.atlas.core.store.SafeFile files) {
		this.files = files;
		return this;
	}

	public Path file() {
		return this.file;
	}

	public synchronized List<Waypoint> all() {
		return List.copyOf(this.waypoints);
	}

	public synchronized List<Waypoint> in(String dimension) {
		return this.waypoints.stream().filter(w -> w.dimension().equals(dimension)).toList();
	}

	public synchronized Waypoint get(String id) {
		for (Waypoint w : this.waypoints) {
			if (w.id().equals(id)) {
				return w;
			}
		}
		return null;
	}

	/** Adds or replaces (by id). */
	public synchronized void put(Waypoint waypoint) {
		for (int i = 0; i < this.waypoints.size(); i++) {
			if (this.waypoints.get(i).id().equals(waypoint.id())) {
				this.waypoints.set(i, waypoint);
				this.version++;
				return;
			}
		}
		this.waypoints.add(waypoint);
		this.version++;
	}

	public synchronized boolean remove(String id) {
		boolean removed = this.waypoints.removeIf(w -> w.id().equals(id));
		if (removed) {
			this.version++;
		}
		return removed;
	}

	/** Records a death point, keeping only the latest {@value #DEATHS_KEPT} per dimension. Returns it. */
	public synchronized Waypoint addDeath(int x, int y, int z, String dimension, long when) {
		return this.addDeath(x, y, z, dimension, when, 0);
	}

	/** Records a death point that goes at {@code expires} (0: never), keeping the latest {@value #DEATHS_KEPT} per dimension. */
	public synchronized Waypoint addDeath(int x, int y, int z, String dimension, long when, long expires) {
		Waypoint death = new Waypoint(java.util.UUID.randomUUID().toString(), "Death", x, y, z, dimension, 5, Waypoint.Kind.DEATH, when, true, expires);
		this.waypoints.add(death);
		List<Waypoint> deaths = this.waypoints.stream()
			.filter(w -> w.kind() == Waypoint.Kind.DEATH && w.dimension().equals(dimension))
			.sorted(Comparator.comparingLong(Waypoint::created).reversed())
			.toList();
		for (int i = DEATHS_KEPT; i < deaths.size(); i++) {
			Waypoint old = deaths.get(i);
			this.waypoints.removeIf(w -> w.id().equals(old.id()));
		}
		this.version++;
		return death;
	}

	/**
	 * Lets waypoints go that should: expired ones, and death points (if {@code clearDeathsWithin} &gt; 0) the player has
	 * come back to within that many blocks in {@code dimension} (not in the first ten seconds, while they respawn).
	 * Returns those removed.
	 */
	public synchronized List<Waypoint> expire(long now, String dimension, double px, double py, double pz, double clearDeathsWithin) {
		List<Waypoint> gone = new ArrayList<>();
		for (Waypoint w : this.waypoints) {
			boolean reached = clearDeathsWithin > 0 && w.kind() == Waypoint.Kind.DEATH && w.dimension().equals(dimension)
				&& w.distance(px, py, pz) <= clearDeathsWithin && Math.abs(w.y() - py) <= clearDeathsWithin + 4 && now - w.created() > 10_000;
			if (w.expired(now) || reached) {
				gone.add(w);
			}
		}
		if (!gone.isEmpty()) {
			this.waypoints.removeAll(gone);
			this.version++;
		}
		return gone;
	}

	private static boolean parses(String json) {
		try {
			return JsonParser.parseString(json).getAsJsonObject().has("waypoints");
		} catch (RuntimeException e) {
			return false;
		}
	}

	public synchronized long version() {
		return this.version;
	}

	public synchronized void load() {
		this.waypoints.clear();
		String text = null;
		if (this.files != null) {
			var r = this.files.read(this.file, "waypoints", VERSION, WaypointStore::parses);
			if (!r.present()) {
				return;
			}
			text = r.payload();
		} else if (!Files.isRegularFile(this.file)) {
			return;
		}
		try {
			if (text == null) {
				text = Files.readString(this.file, StandardCharsets.UTF_8);
			}
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("waypoints")) {
				JsonObject o = e.getAsJsonObject();
				this.waypoints.add(new Waypoint(
					o.get("id").getAsString(),
					o.get("name").getAsString(),
					o.get("x").getAsInt(),
					o.get("y").getAsInt(),
					o.get("z").getAsInt(),
					o.get("dimension").getAsString(),
					o.has("colour") ? o.get("colour").getAsInt() : 0,
					o.has("kind") ? Waypoint.Kind.valueOf(o.get("kind").getAsString()) : Waypoint.Kind.USER,
					o.has("created") ? o.get("created").getAsLong() : 0L,
					!o.has("visible") || o.get("visible").getAsBoolean(),
					o.has("expires") ? o.get("expires").getAsLong() : 0L,
					o.has("icon") ? o.get("icon").getAsString() : "PIN",
					o.has("within") ? o.get("within").getAsInt() : 0,
					o.has("shared") && o.get("shared").getAsBoolean()));
			}
		} catch (IOException | RuntimeException e) {
			this.waypoints.clear();
			this.warn.accept("Atlas: " + this.file + " is unreadable (" + e.getMessage() + "); kept as .corrupt, starting with no waypoints");
			try {
				Files.move(this.file, this.file.resolveSibling(this.file.getFileName() + ".corrupt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// it stays where it is; the next save replaces it
			}
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonArray list = new JsonArray();
		for (Waypoint w : this.all()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", w.id());
			o.addProperty("name", w.name());
			o.addProperty("x", w.x());
			o.addProperty("y", w.y());
			o.addProperty("z", w.z());
			o.addProperty("dimension", w.dimension());
			o.addProperty("colour", w.colour());
			o.addProperty("kind", w.kind().name());
			o.addProperty("created", w.created());
			o.addProperty("visible", w.visible());
			if (w.expires() > 0) {
				o.addProperty("expires", w.expires());
			}
			o.addProperty("icon", w.icon());
			if (w.within() > 0) {
				o.addProperty("within", w.within());
			}
			if (w.shared()) {
				o.addProperty("shared", true);
			}
			list.add(o);
		}
		root.add("waypoints", list);
		String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
		if (this.files != null) {
			this.files.backup(this.file);
			this.files.write(this.file, "waypoints", VERSION, json);
		} else {
			RegionStore.write(this.file, json.getBytes(StandardCharsets.UTF_8));
		}
	}
}
