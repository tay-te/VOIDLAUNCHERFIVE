package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The palette: tone curve, hillshade quantisation, elevation bands, water depth and shoreline, the dithered edge, and
 * golden images of small synthetic worlds drawn end to end. To accept a deliberate change of look, run
 * {@code ./gradlew test -Patlas.golden.update=true} and review the images in {@code src/test/resources/golden}.
 */
class PaletteTest {
	private static float lightness(int rgb) {
		return Oklab.fromRgb(rgb)[0];
	}

	@Test
	void waterDarkensInSixStepsWithDepthAndTheShoreIsLighter() {
		int[] depths = {1, 2, 4, 8, 14, 30};
		Set<Integer> colours = new HashSet<>();
		float last = Float.MAX_VALUE;
		for (int d : depths) {
			int c = Palette.water(d, false, 0x3F76E4);
			float l = lightness(c);
			assertTrue(l < last, "deeper water must be darker (depth " + d + ")");
			last = l;
			colours.add(c);
		}
		assertEquals(6, colours.size(), "six distinct depth steps");
		// within a step the colour does not change: it is stepped, not graded
		assertEquals(Palette.water(4, false, 0x3F76E4), Palette.water(6, false, 0x3F76E4));
		int shore = Palette.water(1, true, 0x3F76E4);
		assertTrue(lightness(shore) > lightness(Palette.water(1, false, 0x3F76E4)), "the shoreline band is the lightest water");
		// water is a deep, muted teal-navy: blue over red, dark
		int deep = Palette.water(40, false, 0x3F76E4);
		assertTrue((deep & 0xFF) > (deep >> 16 & 0xFF), "blue-ish");
		assertTrue(lightness(deep) < 0.25F, "deep water is dark");
	}

	@Test
	void theBiomeOnlyTintsTheWater() {
		int plain = Palette.water(8, false, 0x3F76E4);
		int swamp = Palette.water(8, false, 0x617B64);
		assertTrue(Math.abs(lightness(plain) - lightness(swamp)) < 0.01F, "same depth, same lightness");
		assertTrue(plain != swamp, "but a little different");
	}

	@Test
	void hillshadeIsQuantisedToFiveSteps() {
		assertEquals(0, Palette.shadeStep(0));
		assertEquals(1, Palette.shadeStep(1));
		assertEquals(1, Palette.shadeStep(3));
		assertEquals(2, Palette.shadeStep(4));
		assertEquals(2, Palette.shadeStep(40));
		assertEquals(-1, Palette.shadeStep(-1));
		assertEquals(-1, Palette.shadeStep(-3));
		assertEquals(-2, Palette.shadeStep(-4));
		assertEquals(-2, Palette.shadeStep(-100));
		float[] stone = Palette.tone(Material.STONE, 0x7E7E7E);
		Set<Integer> seen = new HashSet<>();
		float last = -1;
		for (int dh = -10; dh <= 10; dh++) {
			int c = Palette.land(stone, Material.STONE, Palette.shadeStep(dh), 0);
			seen.add(c);
			assertTrue(lightness(c) >= last - 1e-4, "facing the light is never darker");
			last = lightness(c);
		}
		assertEquals(5, seen.size(), "five shades, whatever the slope");
	}

	@Test
	void mountainsBrightenInBandsUpToTheSnow() {
		float[] stone = Palette.tone(Material.STONE, 0x7E7E7E);
		float[] snow = Palette.tone(Material.SNOW, 0xF0FAFA);
		int lastBand = -1;
		float last = 0;
		for (int h = 60; h <= 200; h++) {
			int band = Palette.band(h);
			float l = lightness(Palette.land(stone, Material.STONE, 0, band));
			if (band == lastBand) {
				assertEquals(last, l, 1e-6, "flat within a band: stepped terraces, not a gradient");
			} else {
				assertTrue(l > last, "each band higher is lighter");
			}
			lastBand = band;
			last = l;
		}
		int high = Palette.band(150);
		assertTrue(lightness(Palette.land(snow, Material.SNOW, 0, high)) > lightness(Palette.land(stone, Material.STONE, 0, high)) + 0.08F,
			"snow caps stand out from the rock");
		assertTrue(lightness(Palette.land(snow, Material.SNOW, 2, Palette.MAX_BANDS)) < 0.75F, "even lit snow stays a soft grey, not white");
		assertEquals(0, Palette.band(63));
		assertEquals(0, Palette.band(-20));
	}

	@Test
	void nothingOnTheMapIsLoud() {
		int[] raw = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0x000000, 0x6E9C47, 0xBE6621};
		for (Material m : Material.values()) {
			if (m == Material.NONE || m == Material.WATER) {
				continue;
			}
			for (int c : raw) {
				float[] t = Palette.tone(m, c);
				for (int step = -2; step <= 2; step++) {
					int out = Palette.land(t, m, step, 6);
					float[] lab = Oklab.fromRgb(out);
					assertTrue(Oklab.chroma(lab) <= Palette.MAX_CHROMA + 0.012F, m + " from " + Integer.toHexString(c) + " is too colourful");
					assertTrue(lab[0] < 0.8F, m + " too bright");
				}
			}
		}
	}

	@Test
	void theToneKeepsDifferencesWithinAMaterial() {
		float[] sand = Palette.tone(Material.SAND, 0xDBD3A0);
		float[] redSand = Palette.tone(Material.SAND, 0xBE6621);
		// red sand is warmer (more red over blue) than sand, but still muted
		int a = Oklab.toRgb(sand);
		int b = Oklab.toRgb(redSand);
		assertTrue(((b >> 16 & 0xFF) - (b & 0xFF)) > ((a >> 16 & 0xFF) - (a & 0xFF)), "red sand reads warmer");
		// the typical block of a material lands on its anchor
		float[] grass = Palette.tone(Material.GRASS, Material.GRASS.reference);
		float[] anchor = Oklab.fromRgb(Material.GRASS.anchor);
		assertEquals(anchor[0], grass[0], 0.002);
		assertEquals(anchor[1], grass[1], 0.002);
	}

	@Test
	void theExploredEdgeIsATwoPixelDither() {
		assertEquals(0, Palette.coverage(0));
		assertEquals(128, Palette.coverage(1));
		assertEquals(192, Palette.coverage(2));
		assertEquals(255, Palette.coverage(3));
		// half coverage is a checkerboard, aligned to the global grid
		for (int z = -4; z < 4; z++) {
			for (int x = -4; x < 4; x++) {
				assertEquals(((x + z) & 1) == 0, Palette.shows(128, x, z), "checker at " + x + "," + z);
			}
		}
		int on = 0;
		for (int z = 0; z < 4; z++) {
			for (int x = 0; x < 4; x++) {
				on += Palette.shows(192, x, z) ? 1 : 0;
			}
		}
		assertEquals(12, on, "three quarters shows three pixels in four");
		assertTrue(Palette.shows(255, 7, 3));
		assertTrue(!Palette.shows(0, 7, 3));
	}

	// --- golden images ---------------------------------------------------------------------------------------------

	@Test
	void goldenIsland() throws IOException {
		golden("island", Scenes.draw(Scenes.island(96), 96, TileRenderer.Mode.SURFACE), 96);
	}

	@Test
	void goldenRamps() throws IOException {
		golden("ramps", Scenes.draw(Scenes.ramps(64), 64, TileRenderer.Mode.SURFACE), 64);
	}

	@Test
	void goldenCaves() throws IOException {
		golden("caves", Scenes.draw(Scenes.caves(80), 80, TileRenderer.Mode.CAVE), 80);
	}

	/** The pixels as the GPU shows them: dithered by coverage, over the map's well. */
	static BufferedImage picture(int[] pixels, int size) {
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int p = pixels[z * size + x];
				img.setRGB(x, z, Palette.shows(p >>> 24, x, z) ? p & 0xFFFFFF : Palette.UNEXPLORED);
			}
		}
		return img;
	}

	private static void golden(String name, int[] pixels, int size) throws IOException {
		BufferedImage actual = picture(pixels, size);
		Path out = Path.of(System.getProperty("atlas.test.out", "build/test-images"));
		Files.createDirectories(out);
		// a 6x copy to look at
		BufferedImage big = new BufferedImage(size * 6, size * 6, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < size * 6; y++) {
			for (int x = 0; x < size * 6; x++) {
				big.setRGB(x, y, actual.getRGB(x / 6, y / 6));
			}
		}
		ImageIO.write(big, "png", out.resolve(name + "_x6.png").toFile());
		if (Boolean.getBoolean("atlas.golden.update")) {
			Path dir = Path.of(System.getProperty("atlas.golden.dir", "src/test/resources/golden"));
			Files.createDirectories(dir);
			ImageIO.write(actual, "png", dir.resolve(name + ".png").toFile());
			return;
		}
		BufferedImage expected;
		try (InputStream in = PaletteTest.class.getResourceAsStream("/golden/" + name + ".png")) {
			if (in == null) {
				fail("no golden image for " + name + "; run ./gradlew test -Patlas.golden.update=true and review it");
				return;
			}
			expected = ImageIO.read(in);
		}
		int differ = 0;
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				if ((expected.getRGB(x, z) & 0xFFFFFF) != (actual.getRGB(x, z) & 0xFFFFFF)) {
					differ++;
				}
			}
		}
		if (differ > 0) {
			ImageIO.write(actual, "png", out.resolve(name + "_actual.png").toFile());
			fail(name + ": " + differ + " pixels differ from the golden image (actual written to " + out + ")");
		}
	}
}
