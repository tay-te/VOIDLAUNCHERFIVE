package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.core.waypoints.WaypointShare.ShareFormatException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Share codes: round trips, compactness, tolerance of pasting, and refusal of damaged, hostile or future codes. */
class WaypointShareTest {
	private static String code(String json) {
		return code(1, json);
	}

	private static String code(int version, String json) {
		return "atlas-wp:" + version + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void oneWaypointRoundTrips() {
		SharedWaypoint home = new SharedWaypoint("Home", 100, 64, -200, DimensionIds.OVERWORLD, 2, "by the lake", 1_800_000_000_000L);
		String code = WaypointShare.encode(home);
		assertTrue(code.startsWith("atlas-wp:1:"));
		assertEquals(List.of(home), WaypointShare.decode(code));
	}

	@Test
	void manyWaypointsRoundTripInOrder() {
		List<SharedWaypoint> list = List.of(
			SharedWaypoint.of("Home", 100, 64, -200, DimensionIds.OVERWORLD, 0),
			SharedWaypoint.of("Hub", 12, null, -25, DimensionIds.NETHER, 5),
			SharedWaypoint.of("City", 1200, 90, -800, DimensionIds.END, 3),
			SharedWaypoint.of("Moon base", -3, 7, 9, "mymod:moon", 7),
			SharedWaypoint.of("Ünïcödé 🗺 name", -30_000_000, -2048, 30_000_000, DimensionIds.OVERWORLD, 1));
		assertEquals(list, WaypointShare.decode(WaypointShare.encode(list)));
	}

	@Test
	void codeIsCopyPasteSafe() {
		String code = WaypointShare.encode(SharedWaypoint.of("Base: \"north\" / east", 1, 2, 3, DimensionIds.NETHER, 4));
		String data = code.substring("atlas-wp:1:".length());
		assertTrue(data.matches("[A-Za-z0-9_-]+"), data);
		assertFalse(code.contains(" "));
	}

	@Test
	void codeIsCompactEnoughForChat() {
		// one waypoint with a typical name fits comfortably in a 256-character chat message
		String code = WaypointShare.encode(SharedWaypoint.of("My base", -12345, 64, 67890, DimensionIds.NETHER, 3));
		assertTrue(code.length() < 100, code.length() + ": " + code);
	}

	@Test
	void decodeToleratesWhitespace() {
		String code = WaypointShare.encode(SharedWaypoint.of("Home", 1, 2, 3, DimensionIds.OVERWORLD, 0));
		String messy = "  \n" + code.substring(0, 20) + "\r\n " + code.substring(20) + "\t ";
		assertEquals("Home", WaypointShare.decode(messy).get(0).name());
	}

	@Test
	void prefixIsCaseInsensitive() {
		String code = WaypointShare.encode(SharedWaypoint.of("Home", 1, 2, 3, DimensionIds.OVERWORLD, 0));
		assertEquals(1, WaypointShare.decode("ATLAS-WP:" + code.substring(9)).size());
	}

	@Test
	void newerVersionIsRefusedClearly() {
		ShareFormatException e = assertThrows(ShareFormatException.class, () -> WaypointShare.decode(code(2, "{\"w\":[{\"x\":1,\"z\":2}]}")));
		assertTrue(e.newerVersion());
		assertTrue(e.getMessage().contains("newer version of Atlas"), e.getMessage());
		assertTrue(e.getMessage().contains("Update Atlas"), e.getMessage());
	}

	@Test
	void damagedCodesAreRefused() {
		String good = WaypointShare.encode(SharedWaypoint.of("Home", 1, 2, 3, DimensionIds.OVERWORLD, 0));
		for (String bad : new String[]{
			null, "", "   ", "hello", "atlas-wp:", "atlas-wp:1", "atlas-wp:1:", "atlas-wp:x:abc", "atlas-wp:0:" + good.substring(11),
			"atlas-wp:-1:abc", "atlas-wp:1:not base64!!", "atlas-wp:1:A", "xaero-waypoint:Home:H:1:2:3",
			code("not json {"), code("[1,2,3]"), code("{\"w\":[]}"), code("{\"w\":{}}"), code("{\"x\":1}"),
			code("{\"w\":[{\"n\":\"no position\"}]}"), code("{\"w\":[{\"x\":\"1\",\"z\":2}]}"), code("{\"w\":[5]}"),
			"atlas-wp:1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{(byte) 0xC3, 0x28, (byte) 0xFF}),
		}) {
			ShareFormatException e = assertThrows(ShareFormatException.class, () -> WaypointShare.decode(bad), String.valueOf(bad));
			assertFalse(e.newerVersion());
			assertFalse(e.getMessage().isBlank());
		}
	}

	@Test
	void tooManyWaypointsAreRefusedBothWays() {
		List<SharedWaypoint> many = new ArrayList<>();
		for (int i = 0; i < WaypointShare.MAX_WAYPOINTS; i++) {
			many.add(SharedWaypoint.of("W" + i, i, 64, -i, DimensionIds.OVERWORLD, i));
		}
		assertEquals(WaypointShare.MAX_WAYPOINTS, WaypointShare.decode(WaypointShare.encode(many)).size());
		many.add(SharedWaypoint.of("one too many", 0, 0, 0, null, 0));
		assertThrows(IllegalArgumentException.class, () -> WaypointShare.encode(many));

		StringBuilder json = new StringBuilder("{\"w\":[");
		for (int i = 0; i <= WaypointShare.MAX_WAYPOINTS; i++) {
			json.append(i == 0 ? "" : ",").append("{\"x\":1,\"z\":2}");
		}
		json.append("]}");
		assertThrows(ShareFormatException.class, () -> WaypointShare.decode(code(json.toString())));
	}

	@Test
	void overlongCodeIsRefusedBeforeDecoding() {
		String huge = "atlas-wp:1:" + "A".repeat(WaypointShare.MAX_CODE_LENGTH);
		ShareFormatException e = assertThrows(ShareFormatException.class, () -> WaypointShare.decode(huge));
		assertTrue(e.getMessage().contains("too long"));
	}

	@Test
	void fullSizeShareStillFits() {
		List<SharedWaypoint> many = new ArrayList<>();
		for (int i = 0; i < WaypointShare.MAX_WAYPOINTS; i++) {
			many.add(new SharedWaypoint("N".repeat(60), -29_999_999, -2000, 29_999_999, "somemod:some_long_dimension_name", 3,
				"n".repeat(400), Long.MAX_VALUE));
		}
		String code = WaypointShare.encode(many);
		assertTrue(code.length() <= WaypointShare.MAX_CODE_LENGTH, "length " + code.length());
		assertEquals(many, WaypointShare.decode(code));
	}

	@Test
	void namesAndNotesAreClamped() {
		SharedWaypoint w = new SharedWaypoint("x".repeat(100), 0, 0, 0, null, 0, "y".repeat(1000), null);
		assertEquals(SharedWaypoint.MAX_NAME, w.name().length());
		assertEquals(SharedWaypoint.MAX_NOTE, w.note().length());
		// and on the way in, a hand-made code with a long name is clamped too
		List<SharedWaypoint> in = WaypointShare.decode(code("{\"w\":[{\"n\":\"" + "z".repeat(500) + "\",\"x\":1,\"z\":2}]}"));
		assertEquals(SharedWaypoint.MAX_NAME, in.get(0).name().length());
	}

	@Test
	void clampDoesNotSplitSurrogatePairs() {
		String name = "a".repeat(47) + "🗺";
		SharedWaypoint w = SharedWaypoint.of(name, 0, 0, 0, null, 0);
		assertEquals("a".repeat(47), w.name());
	}

	@Test
	void hostileValuesAreHeldToTheWorld() {
		List<SharedWaypoint> in = WaypointShare.decode(code(
			"{\"w\":[{\"n\":\"far\",\"x\":1e300,\"y\":-99999,\"z\":-5e12,\"c\":-3,\"d\":\"not a dim\"},{\"x\":1.9,\"z\":-1.1,\"y\":64.7}]}"));
		SharedWaypoint far = in.get(0);
		assertEquals(30_000_000, far.x());
		assertEquals(-2048, far.y());
		assertEquals(-30_000_000, far.z());
		assertEquals(5, far.colour());
		assertEquals(DimensionIds.OVERWORLD, far.dimension());
		SharedWaypoint frac = in.get(1);
		assertEquals(1, frac.x());
		assertEquals(64, frac.y());
		assertEquals(-2, frac.z());
	}

	@Test
	void defaultsFillGaps() {
		SharedWaypoint w = WaypointShare.decode(code("{\"w\":[{\"x\":1,\"z\":2}]}")).get(0);
		assertEquals("Waypoint", w.name());
		assertNull(w.y());
		assertEquals(DimensionIds.OVERWORLD, w.dimension());
		assertEquals(0, w.colour());
		assertNull(w.note());
		assertNull(w.expiresAt());
	}

	@Test
	void unknownFieldsAreIgnoredForForwardCompatibility() {
		List<SharedWaypoint> in = WaypointShare.decode(code("{\"w\":[{\"x\":1,\"z\":2,\"n\":\"A\",\"icon\":\"star\"}],\"from\":\"Steve\"}"));
		assertEquals("A", in.get(0).name());
	}

	@Test
	void dimensionsAreWrittenShort() {
		String code = WaypointShare.encode(SharedWaypoint.of("A", 1, 2, 3, DimensionIds.NETHER, 0));
		String json = new String(Base64.getUrlDecoder().decode(code.substring(11)), StandardCharsets.UTF_8);
		assertTrue(json.contains("\"d\":\"n\""), json);
		String ow = WaypointShare.encode(SharedWaypoint.of("A", 1, 2, 3, DimensionIds.OVERWORLD, 0));
		assertFalse(new String(Base64.getUrlDecoder().decode(ow.substring(11)), StandardCharsets.UTF_8).contains("\"d\""));
	}

	@Test
	void expiry() {
		SharedWaypoint w = new SharedWaypoint("Raid", 0, 64, 0, null, 0, null, 1000L);
		assertFalse(w.expired(999));
		assertTrue(w.expired(1000));
		assertFalse(SharedWaypoint.of("Forever", 0, 0, 0, null, 0).expired(Long.MAX_VALUE));
	}

	@Test
	void encodeRefusesNothing() {
		assertThrows(IllegalArgumentException.class, () -> WaypointShare.encode(List.of()));
		assertThrows(IllegalArgumentException.class, () -> WaypointShare.encode((List<SharedWaypoint>) null));
	}

	@Test
	void codesAreFoundInsideChat() {
		String a = WaypointShare.encode(SharedWaypoint.of("A", 1, 2, 3, null, 0));
		String b = WaypointShare.encode(SharedWaypoint.of("B", 4, 5, 6, DimensionIds.NETHER, 1));
		List<String> found = WaypointShare.findCodes("<Steve> here: " + a + " and also " + b + ".");
		assertEquals(List.of(a, b), found);
		assertTrue(WaypointShare.findCodes("nothing here").isEmpty());
		assertTrue(WaypointShare.looksLikeCode("  " + a));
		assertFalse(WaypointShare.looksLikeCode("atlas"));
	}

	@Test
	void convertsToAndFromPlayerWaypoints() {
		Waypoint mine = Waypoint.create("Home", 100, 64, -200, DimensionIds.NETHER, 3);
		SharedWaypoint shared = SharedWaypoint.from(mine);
		assertEquals(new SharedWaypoint("Home", 100, 64, -200, DimensionIds.NETHER, 3, null, null), shared);
		Waypoint back = SharedWaypoint.of("Flat", 5, null, 6, null, 9).toWaypoint(70);
		assertEquals(70, back.y());
		assertEquals(1, back.colour());
		assertEquals(DimensionIds.OVERWORLD, back.dimension());
	}
}
