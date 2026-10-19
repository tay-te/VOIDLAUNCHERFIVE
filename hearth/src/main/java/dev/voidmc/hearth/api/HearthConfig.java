package dev.voidmc.hearth.api;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each module's settings live in their own file, {@code config/hearth/<name>.json}, described by a codec whose fields
 * are all {@code optionalFieldOf(name, default)} so an old file keeps working when fields are added. A missing file is
 * written out with the defaults; one that is not JSON is logged and the defaults are used. A file with a bad value (out
 * of range, the wrong type) keeps every other value it sets: the bad ones are logged by name and fall back to their
 * defaults, so one typo does not silently throw away a server's whole configuration.
 */
public final class HearthConfig {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	private HearthConfig() {
	}

	public static <T> T load(String name, Codec<T> codec, T defaults) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("hearth").resolve(name + ".json");
		try {
			if (Files.exists(file)) {
				return parse(name, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)), codec, defaults);
			}
			Files.createDirectories(file.getParent());
			JsonElement json = codec.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			LOG.warn("could not read config/hearth/{}.json, using defaults", name, e);
		}
		return defaults;
	}

	/**
	 * Reads a config file's JSON. If it does not decode as a whole, it is salvaged value by value: starting from the
	 * defaults, each value the file sets is put in and kept if the whole still decodes, so only the bad ones are lost.
	 */
	public static <T> T parse(String name, JsonElement json, Codec<T> codec, T defaults) {
		var strict = codec.parse(JsonOps.INSTANCE, json);
		if (strict.result().isPresent()) {
			return strict.result().get();
		}
		JsonElement base;
		try {
			base = codec.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
		} catch (RuntimeException e) {
			LOG.warn("config/hearth/{}.json: {}; using defaults", name, strict.error().map(Object::toString).orElse("unreadable"));
			return defaults;
		}
		if (!json.isJsonObject() || !base.isJsonObject()) {
			LOG.warn("config/hearth/{}.json: {}; using defaults", name, strict.error().map(Object::toString).orElse("unreadable"));
			return defaults;
		}
		JsonObject merged = base.getAsJsonObject().deepCopy();
		List<String> rejected = new ArrayList<>();
		salvage(codec, merged, merged, json.getAsJsonObject(), "", rejected);
		T result = codec.parse(JsonOps.INSTANCE, merged).result().orElse(defaults);
		LOG.warn("config/hearth/{}.json: bad values, their defaults used instead: {}", name, String.join(", ", rejected));
		return result;
	}

	/** Puts each of {@code from}'s values into {@code into} (part of {@code root}), keeping those {@code root} still decodes with. */
	private static void salvage(Codec<?> codec, JsonObject root, JsonObject into, JsonObject from, String path, List<String> rejected) {
		for (Map.Entry<String, JsonElement> e : from.entrySet()) {
			String key = e.getKey();
			JsonElement value = e.getValue();
			JsonElement current = into.get(key);
			if (value.isJsonObject() && current == null) {
				// a section left at its defaults is not written out at all (optional fields equal to their default are
				// omitted): an empty one stands for it, if it reads as one
				into.add(key, new JsonObject());
				if (codec.parse(JsonOps.INSTANCE, root).result().isPresent()) {
					current = into.get(key);
				} else {
					into.remove(key);
				}
			}
			if (value.isJsonObject() && current != null && current.isJsonObject()) {
				// a section: its values one by one
				salvage(codec, root, current.getAsJsonObject(), value.getAsJsonObject(), path + key + ".", rejected);
				continue;
			}
			into.add(key, value);
			if (codec.parse(JsonOps.INSTANCE, root).result().isEmpty()) {
				if (current == null) {
					into.remove(key);
				} else {
					into.add(key, current);
				}
				rejected.add(path + key + " = " + value);
			}
		}
	}
}
