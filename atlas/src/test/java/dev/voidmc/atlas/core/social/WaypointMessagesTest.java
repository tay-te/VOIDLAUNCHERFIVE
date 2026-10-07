package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.core.waypoints.DimensionIds;
import dev.voidmc.atlas.core.waypoints.SharedWaypoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Waypoint messages: the share code inside a small envelope, checked like every social message. */
class WaypointMessagesTest {
	static final String SERVER = ServerHash.of("play.example.com");

	@Test
	void roundTripsWithTheDocumentedFields() throws IOException {
		List<SharedWaypoint> list = List.of(SharedWaypoint.of("Home", 1, 64, 2, DimensionIds.OVERWORLD, 3), SharedWaypoint.of("Hub", 5, null, 6, DimensionIds.NETHER, 1));
		byte[] msg = WaypointMessages.encode(new WaypointMessages.Message("bob", SERVER, list));
		JsonObject o = JsonParser.parseString(new String(msg, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(Set.of("t", "v", "player", "server", "code"), o.keySet());
		assertEquals("atlas.waypoint", o.get("t").getAsString());
		assertTrue(o.get("code").getAsString().startsWith("atlas-wp:1:"));
		WaypointMessages.Message back = WaypointMessages.decode(msg);
		assertEquals(list, back.waypoints());
		assertEquals("bob", back.player());
	}

	@Test
	void checksSenderPrivacyAndServer() {
		Roster roster = new Roster();
		roster.setFriends(List.of("bob"));
		byte[] msg = WaypointMessages.encode(new WaypointMessages.Message("bob", SERVER, List.of(SharedWaypoint.of("A", 0, 0, 0, null, 0))));
		assertEquals(Received.ACCEPTED, WaypointMessages.check("me", SERVER, Privacy.FRIENDS, roster, "bob", msg));
		assertEquals(Received.NOT_ALLOWED, WaypointMessages.check("me", SERVER, Privacy.PARTY, roster, "bob", msg));
		assertEquals(Received.NOT_ALLOWED, WaypointMessages.check("me", SERVER, Privacy.NOBODY, roster, "bob", msg));
		assertEquals(Received.OTHER_SERVER, WaypointMessages.check("me", ServerHash.of("x.net"), Privacy.FRIENDS, roster, "bob", msg));
		roster.setFriends(List.of("bob", "eve"));
		assertEquals(Received.WRONG_SENDER, WaypointMessages.check("me", SERVER, Privacy.FRIENDS, roster, "eve", msg));
		assertEquals(Received.FROM_SELF, WaypointMessages.check("bob", SERVER, Privacy.FRIENDS, roster, "bob", msg));
		assertEquals(Received.MALFORMED, WaypointMessages.check("me", SERVER, Privacy.FRIENDS, roster, "bob",
			"{\"t\":\"atlas.waypoint\",\"v\":1,\"player\":\"bob\",\"server\":\"s\",\"code\":\"atlas-wp:1:!!\"}".getBytes(StandardCharsets.UTF_8)));
		assertThrows(IOException.class, () -> WaypointMessages.decode(new byte[] {1, 2, 3}));
	}
}
