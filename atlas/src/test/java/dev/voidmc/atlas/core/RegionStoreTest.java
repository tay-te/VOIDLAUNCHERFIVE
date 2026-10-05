package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The tile store: raw regions and their images written, read back, partly read, and recovered from damage. */
class RegionStoreTest {
	@TempDir
	Path dir;

	private final List<String> warnings = new ArrayList<>();

	private RegionStore store() {
		return new RegionStore(this.dir, this.warnings::add);
	}

	@Test
	void rawRegionsRoundTrip() throws IOException {
		RegionRaw raw = Scenes.island(128);
		raw.set(511, 511, -64, 9, 200, 3, RegionRaw.KNOWN | RegionRaw.ROCK);
		RegionStore store = this.store();
		assertTrue(raw.dirty());
		store.saveRaw("surface", raw);
		assertFalse(raw.dirty(), "saved");
		RegionRaw back = store.loadRaw("surface", 0, 0);
		assertNotNull(back);
		assertArrayEquals(raw.height, back.height);
		assertArrayEquals(raw.block, back.block);
		assertArrayEquals(raw.depth, back.depth);
		assertArrayEquals(raw.biome, back.biome);
		assertArrayEquals(raw.flags, back.flags);
		assertEquals(200, back.depth(RegionRaw.index(511, 511)));
		assertFalse(back.dirty());
		assertTrue(store.hasRaw("surface", 0, 0));
		assertFalse(Files.exists(store.rawFile("surface", 0, 0).resolveSibling("r.0.0.raw.tmp")), "no temporary file left behind");
		assertEquals(1, store.regions("surface").length);
	}

	@Test
	void negativeRegionsHaveTheirOwnFiles() throws IOException {
		RegionStore store = this.store();
		RegionRaw a = new RegionRaw(-1, -3);
		a.set(0, 0, 70, 1, 0, 1, RegionRaw.KNOWN);
		store.saveRaw("cave_-2", a);
		assertNotNull(store.loadRaw("cave_-2", -1, -3));
		assertNull(store.loadRaw("cave_-2", 1, 3));
		long[] keys = store.regions("cave_-2");
		assertEquals(1, keys.length);
		assertEquals(-1, Coords.unpackX(keys[0]));
		assertEquals(-3, Coords.unpackZ(keys[0]));
	}

	@Test
	void imagesRoundTripAndLoadOneLevelAlone() throws IOException {
		RegionImage image = new RegionImage(4, -2, Palette.VERSION);
		RegionRaw[] around = new RegionRaw[9];
		around[4] = Scenes.island(512);
		for (int level = 0; level < RegionImage.LEVELS; level++) {
			image.levelOrCreate(level);
		}
		new TileRenderer(Scenes.LOOKS).render(around, image, 0, 0, 512, 512, TileRenderer.Mode.SURFACE);
		RegionStore store = this.store();
		store.saveImage("surface", image);
		RegionImage all = store.loadImage("surface", 4, -2, (1 << RegionImage.LEVELS) - 1, Palette.VERSION);
		assertNotNull(all);
		for (int level = 0; level < RegionImage.LEVELS; level++) {
			assertArrayEquals(image.level(level), all.level(level), "level " + level);
		}
		RegionImage one = store.loadImage("surface", 4, -2, 1 << 3, Palette.VERSION);
		assertNotNull(one);
		assertTrue(one.hasLevel(3));
		assertFalse(one.hasLevel(0), "only the asked-for level is inflated");
		assertArrayEquals(image.level(3), one.level(3));
		// an image from another palette is not used (it is drawn again from its raw region)
		assertNull(store.loadImage("surface", 4, -2, 1, Palette.VERSION + 1));
		assertTrue(this.warnings.isEmpty());
	}

	@Test
	void aCorruptRawFileIsMovedAsideAndTheRegionStartsAfresh() throws IOException {
		RegionStore store = this.store();
		RegionRaw raw = Scenes.ramps(64);
		store.saveRaw("surface", raw);
		Path file = store.rawFile("surface", 0, 0);
		byte[] bytes = Files.readAllBytes(file);
		bytes[bytes.length / 2] ^= 0x5A;
		Files.write(file, bytes);
		assertNull(store.loadRaw("surface", 0, 0), "a damaged file is not read as nonsense");
		assertFalse(Files.exists(file));
		assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")), "kept for a post-mortem");
		assertEquals(1, this.warnings.size());
		// and the region can be saved and read again
		store.saveRaw("surface", raw);
		assertNotNull(store.loadRaw("surface", 0, 0));
	}

	@Test
	void truncatedAndForeignFilesAreRecovered() throws IOException {
		RegionStore store = this.store();
		Path raw = store.rawFile("surface", 2, 2);
		Files.createDirectories(raw.getParent());
		Files.write(raw, new byte[]{1, 2, 3});
		assertNull(store.loadRaw("surface", 2, 2));
		Path img = store.imageFile("surface", 2, 2);
		Files.write(img, "not an image at all, just text that happens to be long enough to have a header".getBytes());
		assertNull(store.loadImage("surface", 2, 2, 1, Palette.VERSION), "another palette's id: ignored");
		RegionImage image = new RegionImage(3, 3, Palette.VERSION);
		store.saveImage("surface", image);
		Path file = store.imageFile("surface", 3, 3);
		byte[] bytes = Files.readAllBytes(file);
		Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 7));
		assertNull(store.loadImage("surface", 3, 3, 1, Palette.VERSION), "cut short");
		assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
	}

	@Test
	void framesRejectEveryKindOfDamage() {
		byte[] good = Frame.wrap(0x1234, 1, new byte[]{9, 8, 7, 6});
		assertThrowsIo(() -> Frame.unwrap(0x9999, 1, good));
		assertThrowsIo(() -> Frame.unwrap(0x1234, 2, good));
		assertThrowsIo(() -> Frame.unwrap(0x1234, 1, java.util.Arrays.copyOf(good, good.length - 1)));
		byte[] flipped = good.clone();
		flipped[flipped.length - 1] ^= 1;
		assertThrowsIo(() -> Frame.unwrap(0x1234, 1, flipped));
	}

	private interface Io {
		void run() throws IOException;
	}

	private static void assertThrowsIo(Io io) {
		try {
			io.run();
		} catch (IOException expected) {
			return;
		}
		throw new AssertionError("expected an IOException");
	}

	@Test
	void namePalettesKeepTheirIds() throws IOException {
		NamePalette p = new NamePalette();
		int stone = p.id("minecraft:stone");
		int grass = p.id("minecraft:grass_block");
		assertEquals(stone, p.id("minecraft:stone"));
		assertTrue(p.dirty());
		Path file = this.dir.resolve("blocks.txt");
		p.save(file);
		NamePalette back = NamePalette.load(file);
		assertEquals(stone, back.id("minecraft:stone"));
		assertEquals(grass, back.id("minecraft:grass_block"));
		assertEquals("minecraft:grass_block", back.name(grass));
		assertEquals(grass + 1, back.id("expanse:limestone"), "new names come after");
		assertNull(back.name(0));
	}

	@Test
	void mipsAreCoverageWeighted() {
		int full = 255 << 24 | 0x204060;
		int none = 0;
		int avg = RegionImage.average(full, full, none, none);
		assertEquals(0x204060, avg & 0xFFFFFF, "an unexplored pixel does not darken its explored neighbours");
		assertEquals(128, avg >>> 24, "but halves the coverage");
		assertEquals(0, RegionImage.average(none, none, none, none));
	}
}
