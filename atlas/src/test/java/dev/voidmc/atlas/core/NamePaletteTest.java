package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The names file reads the same whichever system wrote it. */
class NamePaletteTest {
	@TempDir
	Path dir;

	@Test
	void savesWithTheSameLineEndingsOnEverySystem() throws IOException {
		NamePalette palette = new NamePalette();
		palette.id("minecraft:stone");
		palette.id("minecraft:grass_block");
		Path file = this.dir.resolve("names.txt");
		palette.save(file);
		assertEquals("minecraft:stone\nminecraft:grass_block\n", Files.readString(file, StandardCharsets.UTF_8));
		assertFalse(Files.exists(this.dir.resolve("names.txt.tmp")));
	}

	@Test
	void loadsAFileWrittenOnWindows() throws IOException {
		Path file = this.dir.resolve("names.txt");
		// what older builds wrote on Windows (the system's line separator), with a blank line keeping its place
		Files.writeString(file, "minecraft:stone\r\n\r\nminecraft:sand\r\n", StandardCharsets.UTF_8);
		NamePalette palette = NamePalette.load(file);
		assertEquals("minecraft:stone", palette.name(1));
		assertEquals("", palette.name(2));
		assertEquals("minecraft:sand", palette.name(3));
		assertEquals(3, palette.id("minecraft:sand"), "no stray carriage return in the name");
		assertEquals(4, palette.size());
	}

	@Test
	void roundTrips() throws IOException {
		NamePalette palette = new NamePalette();
		for (String name : new String[]{"minecraft:stone", "mod:höhle_☃", "minecraft:water"}) {
			palette.id(name);
		}
		Path file = this.dir.resolve("deep").resolve("names.txt");
		palette.save(file);
		NamePalette back = NamePalette.load(file);
		for (int id = 1; id < palette.size(); id++) {
			assertEquals(palette.name(id), back.name(id));
		}
	}
}
