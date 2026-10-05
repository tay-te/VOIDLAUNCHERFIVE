package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An exported area is the regions' pixels in the right places, the unexplored transparent, and a valid PNG. */
class MapExportTest {
	@TempDir
	Path dir;

	@Test
	void stitchesRegionsAcrossTheOriginAndLeavesTheUnexploredClear() throws IOException {
		int size = Coords.REGION_SIZE;
		// region (-1, 0) is red, region (0, 0) green; (0, -1) was never seen
		MapExport.Pixels pixels = (rx, rz, level) -> {
			if (rz != 0 || level != 0) {
				return null;
			}
			int[] p = new int[size * size];
			java.util.Arrays.fill(p, rx == -1 ? 0xFFFF0000 : rx == 0 ? 0xFF00FF00 : 0);
			return rx == -1 || rx == 0 ? p : null;
		};
		MapExport.Result r = MapExport.export(pixels, -16, -8, 16, 8, this.dir.resolve("out.png"));
		assertEquals(32, r.width());
		assertEquals(16, r.height());
		assertEquals(1, r.blocksPerPixel());
		BufferedImage img = ImageIO.read(r.file().toFile());
		assertEquals(0xFFFF0000, img.getRGB(0, 12), "west of x=0 is region -1");
		assertEquals(0xFF00FF00, img.getRGB(20, 12), "east of it, region 0");
		assertEquals(0, img.getRGB(5, 2) >>> 24, "north of z=0 was never seen: clear");
	}

	@Test
	void pixelsPerBlockScaleUpAndTheUnexploredCanBeFilled() throws IOException {
		int size = Coords.REGION_SIZE;
		MapExport.Pixels pixels = (rx, rz, level) -> {
			if (rx != 0 || rz != 0) {
				return null;
			}
			int[] p = new int[size * size];
			java.util.Arrays.fill(p, 0xFF00FF00);
			return p;
		};
		MapExport.Result r = MapExport.export(pixels, -4, 0, 4, 4, this.dir.resolve("big.png"), 4, 0xFF0E0F11);
		assertEquals(32, r.width());
		assertEquals(16, r.height());
		BufferedImage img = ImageIO.read(r.file().toFile());
		assertEquals(0xFF0E0F11, img.getRGB(3, 3), "west of x=0 was never seen: the well's colour");
		assertEquals(0xFF00FF00, img.getRGB(20, 3), "each block four pixels wide");
		org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{32, 16}, MapExport.size(-4, 0, 4, 4, 4));
		org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{8192, 4}, MapExport.size(0, 0, 8192, 4, 4), "too wide for 4 px a block: 1");
	}

	@Test
	void aBigAreaIsExportedAtACoarserLevel() {
		assertEquals(0, MapExport.levelFor(8192, 100));
		assertEquals(1, MapExport.levelFor(8193, 100));
		assertEquals(2, MapExport.levelFor(30000, 30000));
		assertEquals(Coords.MAX_LEVEL, MapExport.levelFor(10_000_000, 10));
	}
}
