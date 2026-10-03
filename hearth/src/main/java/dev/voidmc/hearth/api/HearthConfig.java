package dev.voidmc.hearth.api;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each module's settings live in their own file, {@code config/hearth/<name>.json}, described by a codec whose fields
 * are all {@code optionalFieldOf(name, default)} so an old file keeps working when fields are added. A missing file is
 * written out with the defaults; a broken one is logged and the defaults are used.
 */
public final class HearthConfig {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	private HearthConfig() {
	}

	public static <T> T load(String name, Codec<T> codec, T defaults) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("hearth").resolve(name + ".json");
		try {
			if (Files.exists(file)) {
				JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				return codec.parse(JsonOps.INSTANCE, json).resultOrPartial(e -> LOG.warn("config/hearth/{}.json: {}", name, e)).orElse(defaults);
			}
			Files.createDirectories(file.getParent());
			JsonElement json = codec.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			LOG.warn("could not read config/hearth/{}.json, using defaults", name, e);
		}
		return defaults;
	}
}
