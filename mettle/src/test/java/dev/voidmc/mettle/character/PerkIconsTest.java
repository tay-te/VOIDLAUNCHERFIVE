package dev.voidmc.mettle.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every shipped perk has the icon {@code Perk#icon} resolves to (textures/gui/perk/<last path segment>.png). */
class PerkIconsTest {
	private static final Path PERKS = Path.of("src/main/resources/data/mettle/mettle/perk");
	private static final Path ICONS = Path.of("src/main/resources/assets/mettle/textures/gui/perk");

	@Test
	void everyPerkHasAnIcon() throws IOException {
		try (Stream<Path> files = Files.walk(PERKS)) {
			List<String> missing = files.filter(p -> p.toString().endsWith(".json"))
				.map(p -> p.getFileName().toString().replace(".json", ""))
				.filter(name -> !Files.exists(ICONS.resolve(name + ".png")))
				.sorted()
				.toList();
			assertEquals(List.of(), missing, "perks without an icon");
		}
	}
}
