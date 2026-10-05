package dev.voidmc.atlas.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.api.Discovery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The structures one world's player has found, in {@code discoveries.json}. Thread-safe. */
public final class DiscoveryStore {
	private final Path file;
	private final Consumer<String> warn;
	private final Map<String, Discovery> discoveries = new LinkedHashMap<>();
	private long version;
	private long savedVersion;

	public DiscoveryStore(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	public synchronized List<Discovery> all() {
		return new ArrayList<>(this.discoveries.values());
	}

	public synchronized boolean has(String id) {
		return this.discoveries.containsKey(id);
	}

	/** Adds a discovery; returns false if it was already known. */
	public synchronized boolean add(Discovery d) {
		if (this.discoveries.putIfAbsent(d.id(), d) == null) {
			this.version++;
			return true;
		}
		return false;
	}

	public synchronized long version() {
		return this.version;
	}

	public synchronized boolean dirty() {
		return this.version != this.savedVersion;
	}

	public synchronized void load() {
		this.discoveries.clear();
		if (!Files.isRegularFile(this.file)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("discoveries")) {
				JsonObject o = e.getAsJsonObject();
				Discovery d = new Discovery(o.get("structure").getAsString(), o.get("name").getAsString(), o.get("category").getAsString(),
					o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("dimension").getAsString(),
					o.get("discovered").getAsLong(), o.get("underground").getAsBoolean());
				this.discoveries.put(d.id(), d);
			}
		} catch (IOException | RuntimeException e) {
			this.discoveries.clear();
			this.warn.accept("Atlas: " + this.file + " is unreadable (" + e.getMessage() + "); kept as .corrupt");
			try {
				Files.move(this.file, this.file.resolveSibling(this.file.getFileName() + ".corrupt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// replaced by the next save
			}
		}
		this.savedVersion = this.version;
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray list = new JsonArray();
		long version;
		synchronized (this) {
			version = this.version;
			for (Discovery d : this.discoveries.values()) {
				JsonObject o = new JsonObject();
				o.addProperty("structure", d.structure());
				o.addProperty("name", d.name());
				o.addProperty("category", d.category());
				o.addProperty("x", d.x());
				o.addProperty("y", d.y());
				o.addProperty("z", d.z());
				o.addProperty("dimension", d.dimension());
				o.addProperty("discovered", d.discovered());
				o.addProperty("underground", d.underground());
				list.add(o);
			}
		}
		root.add("discoveries", list);
		RegionStore.write(this.file, new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
		synchronized (this) {
			this.savedVersion = version;
		}
	}
}
