package dev.voidmc.gloam.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeasonColoursTest {
	private static final int PLAINS_GRASS = 0x91BD59, PLAINS_FOLIAGE = 0x77AB2F;
	private static final float SPRING = 0.15F, SUMMER = 0.36F, AUTUMN = 0.62F, WINTER = 0.87F;

	private static int r(int c) {
		return c >> 16 & 0xFF;
	}

	private static int g(int c) {
		return c >> 8 & 0xFF;
	}

	private static int b(int c) {
		return c & 0xFF;
	}

	@Test
	void noneIsVanilla() {
		assertEquals(PLAINS_GRASS, SeasonColours.NONE.grass(PLAINS_GRASS, 0.8F, 0.4F, 0.02F));
		assertEquals(PLAINS_FOLIAGE, SeasonColours.NONE.foliage(PLAINS_FOLIAGE, 0.8F, 0.4F));
		assertEquals(0, SeasonColours.NONE.bare());
	}

	@Test
	void broadleavesTurnInAutumnAndAreBareInWinter() {
		int summer = SeasonColours.at(SUMMER).foliage(PLAINS_FOLIAGE, 0.8F, 0.4F);
		int autumn = SeasonColours.at(AUTUMN).foliage(PLAINS_FOLIAGE, 0.8F, 0.4F);
		assertTrue(g(summer) > r(summer), "summer leaves are green");
		assertTrue(r(autumn) > g(autumn) && g(autumn) > b(autumn), "autumn leaves are orange: " + Integer.toHexString(autumn));
		assertEquals(0, SeasonColours.at(SUMMER).bare());
		assertEquals(1, SeasonColours.at(WINTER).bare(), 1e-4);
		assertTrue(SeasonColours.at(AUTUMN).turned() > 0.9F);
		// spring: leafed out, fresh and lighter than summer's deep green
		int spring = SeasonColours.at(SPRING).foliage(PLAINS_FOLIAGE, 0.8F, 0.4F);
		assertTrue(SeasonColours.at(SPRING).bare() < 0.05F);
		assertTrue(g(spring) > g(summer));
	}

	@Test
	void grassGoesStrawInWinterButNotOrange() {
		int winter = SeasonColours.at(WINTER).grass(PLAINS_GRASS, 0.8F, 0.4F, 0);
		int summer = SeasonColours.at(SUMMER).grass(PLAINS_GRASS, 0.8F, 0.4F, 0);
		assertTrue(g(summer) - r(summer) > 30);
		// dull: red and green close, both well above blue
		assertTrue(Math.abs(r(winter) - g(winter)) < 20 && b(winter) < g(winter) - 15, Integer.toHexString(winter));
	}

	@Test
	void needlesStayGreen() {
		SeasonColours winter = SeasonColours.at(WINTER);
		int spruce = winter.conifer(SeasonLeaves.SPRUCE, 0.25F, 0.8F);
		assertTrue(g(spruce) > r(spruce) && g(spruce) > b(spruce));
		assertTrue(g(spruce) <= g(SeasonLeaves.SPRUCE), "a little darker in the cold");
	}

	@Test
	void hotAndTropicalLandsChangeLittle() {
		SeasonColours winter = SeasonColours.at(WINTER);
		int savanna = 0xBFB755;
		int hot = winter.grass(savanna, 2.0F, 0.0F, 0);
		assertTrue(Math.abs(r(hot) - r(savanna)) + Math.abs(g(hot) - g(savanna)) + Math.abs(b(hot) - b(savanna)) < 30);
		int jungle = 0x59C93C;
		int wet = winter.foliage(jungle, 0.95F, 0.9F);
		assertTrue(g(wet) > r(wet) + 40, "a jungle stays green: " + Integer.toHexString(wet));
	}

	@Test
	void winterSnowsOnTheHillsAndRainsInTheValleys() {
		SeasonColours winter = SeasonColours.at(WINTER), summer = SeasonColours.at(SUMMER);
		int sea = 63;
		// a plains-warm land (0.8): vanilla's own temperature falls 0.05 per 40 blocks above sea + 17
		float valley = 0.8F, hill = 0.8F - (150 - sea - 17) * 0.05F / 40;
		assertTrue(Snowline.temperature(valley, 0.8F, 70, sea, winter) >= 0.15F, "rain in the valley");
		assertTrue(Snowline.temperature(hill, 0.8F, 150, sea, winter) < 0.15F, "snow on the hill");
		assertTrue(Snowline.temperature(hill, 0.8F, 150, sea, summer) >= 0.15F, "no snow on the hill in summer");
		// a snowy land still snows in summer, but higher than vanilla's line
		assertTrue(Snowline.temperature(-0.5F, -0.5F, 120, sea, summer) < 0.15F);
		assertTrue(Snowline.temperature(0.1F, 0.1F, 90, sea, summer) >= 0.15F);
	}
}
