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

	public WaypointStore(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
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
		Waypoint death = new Waypoint(java.util.UUID.randomUUID().toString(), "Death", x, y, z, dimension, 5, Waypoint.Kind.DEATH, when, true);
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

	public synchronized long version() {
		return this.version;
	}

	public synchronized void load() {
		this.waypoints.clear();
		if (!Files.isRegularFile(this.file)) {
			return;
		}
		try {
			String text = Files.readString(this.file, StandardCharsets.UTF_8);
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
					!o.has("visible") || o.get("visible").getAsBoolean()));
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
			list.add(o);
		}
		root.add("waypoints", list);
		RegionStore.write(this.file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
	}
}
