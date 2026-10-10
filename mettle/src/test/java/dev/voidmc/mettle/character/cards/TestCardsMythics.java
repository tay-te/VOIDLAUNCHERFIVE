package dev.voidmc.mettle.character.cards;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.Mythic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

/** The shipped Mythics, read from the resources. */
final class TestCardsMythics {
	private TestCardsMythics() {
	}

	static Map<Identifier, Mythic> load() {
		TestCards.PERKS.size();
		Map<Identifier, Mythic> out = new HashMap<>();
		Path root = TestCards.DATA.resolve("mythic");
		try (Stream<Path> files = Files.walk(root)) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				String rel = root.relativize(p).toString().replace('\\', '/').replace(".json", "");
				out.put(Identifier.fromNamespaceAndPath("mettle", rel), Mythic.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(p))).getOrThrow());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}
}
