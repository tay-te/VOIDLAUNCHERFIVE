package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The section 9 map art's tables and formulas. */
class MapArtTest {
	@Test
	void minorContoursFadeWhereTheyCrowdTheScreenAndTheMajorsStay() {
		// an even slope of 1 (a minor line every 16 blocks, a major every 64), drawn with and without contours
		RegionRaw raw = new RegionRaw(0, 0);
		int size = 256;
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				raw.set(x, z, 70 + x, Scenes.GRASS, 0, Scenes.PLAINS, RegionRaw.KNOWN);
			}
		}
		RegionImage lined = drawn(raw, size, true);
		RegionImage plain = drawn(raw, size, false);
		// 16 screen pixels apart at 1:1 and 8 at level 1: the lines are there
		assertTrue(maxDifference(lined, plain, 0, size, false) > 8, "minor lines drawn at 1:1");
		assertTrue(maxDifference(lined, plain, 1, size, false) > 3, "and 8 pixels apart");
		// 2 pixels apart at level 3: gone, except where a major line falls
		assertTrue(maxDifference(lined, plain, 3, size, true) <= 1, "minor lines crowding the screen fade");
		assertTrue(maxDifference(lined, plain, 3, size, false) >= 2, "the 64-block lines stay (averaged as ever)");
		assertEquals(1, TileRenderer.minorKeep(0, 1), 1e-6);
		assertEquals(0.5F, TileRenderer.minorKeep(2, 1), 1e-6);
		assertEquals(0, TileRenderer.minorKeep(3, 1), 1e-6);
	}

	@Test
	void cavernWaterOnlyTintsRevealedWater() {
		RegionRaw raw = Scenes.caves(80);
		RegionImage plain = new RegionImage(0, 0, Palette.VERSION);
		RegionImage tinted = new RegionImage(0, 0, Palette.VERSION);
		RegionRaw[] around = new RegionRaw[9];
		around[4] = raw;
		new TileRenderer(Scenes.LOOKS).render(around, plain, 0, 0, 80, 80, TileRenderer.Mode.CAVE);
		TileRenderer r = new TileRenderer(Scenes.LOOKS);
		// the caverns say a buried sea lies under every column, at any height
		r.cavernWater = (x, z, y) -> CavernWater.SEA;
		r.render(around, tinted, 0, 0, 80, 80, TileRenderer.Mode.CAVE);
		int changed = 0;
		for (int z = 0; z < 80; z++) {
			for (int x = 0; x < 80; x++) {
				int i = RegionRaw.index(x, z);
				int p = plain.level(0)[z * Coords.REGION_SIZE + x], q = tinted.level(0)[z * Coords.REGION_SIZE + x];
				boolean water = (raw.flags[i] & RegionRaw.KNOWN) != 0 && raw.depth(i) > 0 && (raw.flags[i] & RegionRaw.ROCK) == 0;
				assertEquals(p >>> 24, q >>> 24, "coverage never changes: nothing is revealed");
				if (!water) {
					assertEquals(p, q, "only water is tinted");
				} else if (p != q) {
					changed++;
				}
			}
		}
		assertTrue(changed > 0, "the revealed pool takes the sea's tint");
		assertEquals(0x123456, Palette.cavernTint(0x123456, CavernWater.NONE));
	}

	private static RegionImage drawn(RegionRaw raw, int size, boolean contours) {
		RegionImage image = new RegionImage(0, 0, Palette.VERSION);
		RegionRaw[] around = new RegionRaw[9];
		around[4] = raw;
		TileRenderer r = new TileRenderer(Scenes.LOOKS);
		r.style = MapArt.Style.TOPOGRAPHIC;
		r.contours = contours;
		r.render(around, image, 0, 0, size, size, TileRenderer.Mode.SURFACE);
		return image;
	}

	/** The largest channel difference at a level, inside the drawn square (away from its edge), optionally only where no major line falls. */
	private static int maxDifference(RegionImage a, RegionImage b, int level, int size, boolean awayFromMajors) {
		int[] pa = a.level(level), pb = b.level(level);
		int row = Coords.regionImageSize(level);
		int n = size >> level, span = 1 << level;
		int max = 0;
		for (int z = 1; z < n - 1; z++) {
			for (int x = 1; x < n - 1; x++) {
				boolean major = false;
				for (int bx = x * span - 1; bx <= (x + 1) * span; bx++) {
					major |= Math.floorDiv(70 + bx, 64) != Math.floorDiv(70 + bx + 1, 64);
				}
				if (awayFromMajors && major) {
					continue;
				}
				int p = pa[z * row + x], q = pb[z * row + x];
				for (int sh = 0; sh <= 16; sh += 8) {
					max = Math.max(max, Math.abs((p >> sh & 0xFF) - (q >> sh & 0xFF)));
				}
			}
		}
		return max;
	}

	@Test
	void theTunedBlocksAreTheTable() {
		assertEquals(0x455331, MapArt.tuned("minecraft:grass_block"));
		assertEquals(0x907554, MapArt.tuned("minecraft:oak_planks"));
		assertEquals(0x626365, MapArt.tuned("minecraft:cobblestone"));
		assertEquals(-1, MapArt.tuned("minecraft:granite"));
		assertEquals(-1, MapArt.tuned("expanse:grass_block"), "a mod's block is not the vanilla one");
	}

	@Test
	void plainsGrassIsTheTableColour() {
		float[] lab = MapArt.tinted(0x455331, MapArt.REF_GRASS, false);
		float[] table = Oklab.fromRgb(0x455331);
		assertEquals(table[0], lab[0], 1e-4);
		assertEquals(table[1], lab[1], 1e-4);
		float[] swamp = MapArt.tinted(0x455331, 0x6A7039, false);
		assertTrue(swamp[0] < lab[0], "a darker tint darkens the grass a little");
		assertTrue(Math.hypot(swamp[1], swamp[2]) <= MapArt.CHROMA_CAP + 1e-6);
	}

	@Test
	void biomeFillsFamiliesAndModdedColours() {
		assertEquals(0x9D7B87, MapArt.vanillaFill("minecraft:cherry_grove"));
		assertEquals(MapArt.Family.FORESTS, MapArt.family("minecraft:dark_forest"));
		assertEquals(MapArt.Family.CAVES, MapArt.family("minecraft:deep_dark"));
		int modded = MapArt.moddedFill(0x79B35A, 0x5DA03A);
		float[] lab = Oklab.fromRgb(modded);
		assertTrue(lab[0] >= 0.39F && lab[0] <= 0.61F, "lightness within the vanilla fills' range");
		assertTrue(Math.hypot(lab[1], lab[2]) <= 0.0505, "quiet");
		assertTrue(lab[2] > 0, "keeps its yellow-green hue");
	}

	@Test
	void waterStepsAndWords() {
		assertEquals(0, MapArt.seaStep(1));
		assertEquals(1, MapArt.seaStep(3));
		assertEquals(4, MapArt.seaStep(19));
		assertEquals(6, MapArt.seaStep(40));
		assertEquals("mild", MapArt.temperatureWord(0.6F));
		assertEquals("frozen", MapArt.temperatureWord(0.0F));
		assertEquals("wet", MapArt.rainfallWord(0.8F));
		assertEquals("Cherry Grove", MapArt.biomeLabel("minecraft:cherry_grove"));
	}

	@Test
	void nightAndGlow() {
		float[] day = {0.6F, 0.02F, 0.03F};
		float[] night = MapArt.moonlit(day);
		assertEquals(0.6F * 0.56F + 0.015F, night[0], 1e-6);
		assertEquals(0, MapArt.glow(14, 10), 1e-6, "a torch's glow ends before 10 blocks");
		assertTrue(MapArt.glow(15, 0) > 0.9F);
		float[] lit = MapArt.lit(night, day, 1, false);
		assertTrue(lit[0] > night[0], "light brings the day back");
	}
}
