package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The importers read folders in the same order on every system. */
class ImportFilesTest {
	@TempDir
	Path dir;

	@Test
	void listsByNameIgnoringCaseOnEverySystem() throws IOException {
		// Path.compareTo ignores case on Windows and not elsewhere: "Zed" would come before "apple" on Linux
		for (String name : new String[]{"Zed.json", "apple.json", "Mine.json", "banana.json"}) {
			Files.writeString(this.dir.resolve(name), "{}");
		}
		List<String> names = ImportFiles.list(this.dir, Files::isRegularFile, s -> {
		}).stream().map(p -> p.getFileName().toString()).toList();
		assertEquals(List.of("apple.json", "banana.json", "Mine.json", "Zed.json"), names);
	}

	@Test
	void namesThatDifferOnlyInCaseHaveAFixedOrder() {
		// on Linux both can exist side by side; on Windows and macOS they cannot, so this is only the comparator
		List<Path> paths = new java.util.ArrayList<>(List.of(this.dir.resolve("world"), this.dir.resolve("World"), this.dir.resolve("WORLD")));
		paths.sort(ImportFiles.BY_NAME);
		assertEquals(List.of("WORLD", "World", "world"), paths.stream().map(p -> p.getFileName().toString()).toList());
	}
}
