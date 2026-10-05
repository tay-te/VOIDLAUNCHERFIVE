package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.waypoints.CoordParser.Form;
import dev.voidmc.atlas.core.waypoints.CoordParser.Match;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Coordinates in chat: every shape players type, the offsets that let the UI link them, and the false positives. */
class CoordParserTest {
	private static Match only(String text) {
		List<Match> all = CoordParser.find(text);
		assertEquals(1, all.size(), () -> "expected one match in '" + text + "' but got " + all);
		return all.get(0);
	}

	private static void xyz(Match m, int x, Integer y, int z) {
		assertEquals(x, m.x(), "x");
		assertEquals(y, m.y(), "y");
		assertEquals(z, m.z(), "z");
	}

	private static void none(String text) {
		List<Match> all = CoordParser.find(text);
		assertTrue(all.isEmpty(), () -> "expected nothing in '" + text + "' but got " + all);
	}

	// ---- the shapes ----

	@Test
	void spaceSeparatedTriple() {
		String text = "meet me at 100 64 -200 ok";
		Match m = only(text);
		xyz(m, 100, 64, -200);
		assertEquals("100 64 -200", text.substring(m.start(), m.end()));
		assertEquals(Form.BARE, m.form());
		assertNull(m.dimension());
		assertNull(m.name());
	}

	@Test
	void commaSeparatedTriple() {
		String text = "base: 100, 64, -200";
		Match m = only(text);
		xyz(m, 100, 64, -200);
		assertEquals("100, 64, -200", text.substring(m.start(), m.end()));
	}

	@Test
	void tightCommaAndSlashTriples() {
		xyz(only("100,64,-200"), 100, 64, -200);
		xyz(only("100/64/-200"), 100, 64, -200);
	}

	@Test
	void pairIsXAndZ() {
		String text = "it's around 100 -200 I think";
		Match m = only(text);
		xyz(m, 100, null, -200);
		assertEquals("100 -200", text.substring(m.start(), m.end()));
		xyz(only("go to 1500, -3200"), 1500, null, -3200);
	}

	@Test
	void labelledColonForm() {
		String text = "X: 100 Y: 64 Z: -200";
		Match m = only(text);
		xyz(m, 100, 64, -200);
		assertEquals(Form.LABELLED, m.form());
		assertEquals(0, m.start());
		assertEquals(text.length(), m.end());
	}

	@Test
	void labelledEqualsForm() {
		Match m = only("I'm at x=100 y=64 z=-200 now");
		xyz(m, 100, 64, -200);
		assertEquals(Form.LABELLED, m.form());
		xyz(only("x = 100, y = 64, z = -200"), 100, 64, -200);
		xyz(only("X:100 Y:64 Z:-200"), 100, 64, -200);
	}

	@Test
	void labelledXzOnly() {
		Match m = only("x: 5 z: 7");
		xyz(m, 5, null, 7);
		assertEquals(Form.LABELLED, m.form());
	}

	@Test
	void labelsAllowSmallNumbers() {
		// labels make it certain, so the digit rule for bare numbers does not apply
		xyz(only("x=1 y=2 z=3"), 1, 2, 3);
	}

	@Test
	void bracketsAreIncludedInTheSpan() {
		String square = "[100, 64, -200]";
		Match a = only("pos " + square + " thanks");
		xyz(a, 100, 64, -200);
		assertEquals(square, ("pos " + square + " thanks").substring(a.start(), a.end()));

		String round = "see (100 / 64 / -200).";
		Match b = only(round);
		xyz(b, 100, 64, -200);
		assertEquals("(100 / 64 / -200)", round.substring(b.start(), b.end()));
	}

	@Test
	void unbalancedBracketsAreNotIncluded() {
		String text = "[100 64 -200";
		Match m = only(text);
		assertEquals("100 64 -200", text.substring(m.start(), m.end()));
	}

	@Test
	void f3Line() {
		String text = "XYZ: 100.500 / 64.00000 / -200.300";
		Match m = only(text);
		xyz(m, 100, 64, -201);
		assertEquals("100.500 / 64.00000 / -200.300", text.substring(m.start(), m.end()));
	}

	@Test
	void coordinatesPrefix() {
		xyz(only("Coordinates: 100, 64, -200"), 100, 64, -200);
	}

	@Test
	void decimalsRoundDown() {
		xyz(only("100.5 64 -200.3"), 100, 64, -201);
		xyz(only("-0.5 64.9 0.5 is close"), -1, 64, 0);
		xyz(only("X: -10.2 Y: 70.9 Z: 3.99"), -11, 70, 3);
	}

	@Test
	void severalMatchesInOrder() {
		String text = "portal 100 64 -200 and base 1200, 70, 3400 also x=5 z=6";
		List<Match> all = CoordParser.find(text);
		assertEquals(3, all.size(), all::toString);
		xyz(all.get(0), 100, 64, -200);
		xyz(all.get(1), 1200, 70, 3400);
		xyz(all.get(2), 5, null, 6);
		assertTrue(all.get(0).end() <= all.get(1).start() && all.get(1).end() <= all.get(2).start());
	}

	@Test
	void sentencePunctuationAfterIsFine() {
		xyz(only("my house is at 100 64 -200."), 100, 64, -200);
		xyz(only("is it 100 64 -200?"), 100, 64, -200);
		xyz(only("100 64 -200!"), 100, 64, -200);
	}

	@Test
	void worldEdgesAreAccepted() {
		xyz(only("30000000 64 -30000000"), 30_000_000, 64, -30_000_000);
		xyz(only("0 -2048 0 bottom"), 0, -2048, 0);
		xyz(only("10 2048 10"), 10, 2048, 10);
	}

	// ---- Xaero ----

	@Test
	void xaeroShare() {
		String text = "[Steve] xaero-waypoint:Home:H:100:64:-200:6:false:0:Internal-overworld-waypoints";
		Match m = only(text);
		xyz(m, 100, 64, -200);
		assertEquals("Home", m.name());
		assertEquals(Form.XAERO, m.form());
		assertEquals(DimensionIds.OVERWORLD, m.dimension());
		assertEquals(text.indexOf("xaero-waypoint"), m.start());
		assertEquals(text.length(), m.end());
	}

	@Test
	void xaeroShareInTheNetherWithUnknownHeightAndEscapedName() {
		Match m = only("xaero-waypoint:Hub§§East:H:12:~:-25:4:false:0:Internal-the_nether-waypoints");
		xyz(m, 12, null, -25);
		assertEquals("Hub:East", m.name());
		assertEquals(DimensionIds.NETHER, m.dimension());
	}

	@Test
	void xaeroShareWithNewerDimensionNamingAndSpaces() {
		Match m = only("look xaero-waypoint:End City:E:1200:90:-800:13:false:0:Internal-dim%minecraft$the_end-waypoints please");
		assertEquals("End City", m.name());
		assertEquals(DimensionIds.END, m.dimension());
		Match n = only("xaero-waypoint:Fort:F:1:70:2:4:false:0:Internal-dim%-1-waypoints");
		assertEquals(DimensionIds.NETHER, n.dimension());
	}

	@Test
	void xaeroShareWithoutTrailingFields() {
		Match m = only("xaero-waypoint:Camp:C:-5:80:7");
		xyz(m, -5, 80, 7);
		assertEquals("Camp", m.name());
		assertNull(m.dimension());
	}

	@Test
	void xaeroOutOfRangeIsDropped() {
		none("xaero-waypoint:Far:F:40000000:64:0:6:false:0:Internal-overworld-waypoints");
	}

	// ---- dimension hints ----

	@Test
	void dimensionWordsBecomeHints() {
		assertEquals(DimensionIds.NETHER, only("nether 100 64 -200").dimension());
		assertEquals(DimensionIds.NETHER, only("in the nether: 100 64 -200").dimension());
		assertEquals(DimensionIds.NETHER, only("100 64 -200 (the_nether)").dimension());
		assertEquals(DimensionIds.NETHER, only("minecraft:the_nether 100 64 -200").dimension());
		assertEquals(DimensionIds.OVERWORLD, only("overworld portal at 800 64 -1600").dimension());
		assertEquals(DimensionIds.END, only("End: 100 50 200").dimension());
		assertEquals(DimensionIds.END, only("the_end gateway 100 50 200").dimension());
		assertEquals(DimensionIds.END, only("in the end 100 50 200").dimension());
	}

	@Test
	void endIsOnlyAHintRightNextToTheNumbers() {
		assertNull(only("at the end of the day my base is 100 64 -200").dimension());
		assertNull(only("100 64 -200 is where it will end").dimension());
	}

	@Test
	void nearestDimensionWordWins() {
		List<Match> all = CoordParser.find("overworld 800 64 -1600 is nether 100 64 -200");
		assertEquals(2, all.size());
		assertEquals(DimensionIds.OVERWORLD, all.get(0).dimension());
		assertEquals(DimensionIds.NETHER, all.get(1).dimension());
	}

	@Test
	void netherPortalIsNotTheNether() {
		// "nether_portal" is one word, not the dimension
		assertNull(only("my nether_portal is at 100 64 -200").dimension());
	}

	// ---- keyword relaxation ----

	@Test
	void keywordLetsSmallNumbersThrough() {
		xyz(only("coords 5 9"), 5, null, 9);
		xyz(only("coords: 1 2 3"), 1, 2, 3);
		xyz(only("tp 1 64 2"), 1, 64, 2);
		xyz(only("pos: [1, 2, 3]"), 1, 2, 3);
		xyz(only("coords 5.5 9.5"), 5, null, 9);
	}

	// ---- false positives ----

	@ParameterizedTest
	@ValueSource(strings = {
		"I have 3 apples and 12 pears",
		"see you at 12:30",
		"see you at 12:30:15",
		"born 2026-10-05",
		"on 2026-10-05 at 10:00",
		"the score was 10-2",
		"we won 3 - 2 lol",
		"update to 1.21.4 today",
		"1.21.4 64 100",
		"it's 1 2 3 go",
		"10 20",
		"5 / 8",
		"100 / 200",
		"version 1.20 and 1.21",
		"costs $100 64 -200",
		"#100 64 -200",
		"50% 64 -200",
		"12 34",
		"v100 64 -200",
		"x100 64 -200",
		"I have 1,500 gold",
		"a population of 12,345,678 people",
		"05/10/2026",
		"5/10/2026 was fun",
		"2026/10/05",
		"31/12/1999",
		"007 64 100",
		"40000000 64 0",
		"100 5000 100",
		"100 -3000 100",
		"1 2 3 4",
		"tp 100 64 -200 90 0",
		"100 64 -200 300",
		"phone 555 0123",
		"3.14 2.71",
		"19.99 12.50",
		"12345678901 64 5",
		"",
		"no numbers here",
		"100",
		"100px 200px",
		"100°C 200",
		"100, 64 -200",
	})
	void falsePositivesAreIgnored(String text) {
		none(text);
	}

	@Test
	void nullIsEmpty() {
		assertTrue(CoordParser.find(null).isEmpty());
		assertNull(CoordParser.first(null));
	}

	@Test
	void firstGivesTheEarliest() {
		Match m = CoordParser.first("a 500 600 b 100 64 -200");
		assertNotNull(m);
		xyz(m, 500, null, 600);
	}

	@Test
	void plainSentenceNumbersStayPlain() {
		// a two-number pair needs five digits between them
		none("I died 12 times in 3 days");
		xyz(only("I walked 1000 2000"), 1000, null, 2000);
	}

	@Test
	void datesWithSpacesAreStillDatesOnlyByLeadingZeroOrYearRules() {
		// spaced slashes are how F3 writes coordinates, so a spaced "date" without a leading zero is read
		xyz(only("5 / 10 / 2026"), 5, 10, 2026);
	}
}
